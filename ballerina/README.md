# Overview

The natural programming library module provides seamless integration with Large Language Models (LLMs). It offers a first-class approach to integrate LLM calls with automatic detection of expected response formats and parsing of responses to corresponding Ballerina types.

This simplifies working with AI models by handling the communication and data conversion automatically.

## The `np:NaturalFunction` annotation

An `external` function annotated with `np:NaturalFunction` and with a `prompt` parameter of type `np:Prompt` becomes an LLM call with the specified prompt. The JSON schema generated from the return type of the function is incorporated to the LLM call and the response from the LLM is automatically parsed to the type used as the return type.

```ballerina
import ballerinax/np;

final readonly & string[] categories = loadCategories();

public type Blog record {|
    string title;
    string content;
|};

type Review record {|
    string? suggestedCategory;
    int rating;
|};

public isolated function reviewBlog(
    Blog blog,
    np:Prompt prompt = `You are an expert content reviewer for a blog site that 
        categorizes posts under the following categories: ${categories}

        Your tasks are:
        1. Suggest a suitable category for the blog from exactly the specified categories. 
           If there is no match, use null.

        2. Rate the blog post on a scale of 1 to 10 based on the following criteria:
        - **Relevance**: How well the content aligns with the chosen category.
        - **Depth**: The level of detail and insight in the content.
        - **Clarity**: How easy it is to read and understand.
        - **Originality**: Whether the content introduces fresh perspectives or ideas.
        - **Language Quality**: Grammar, spelling, and overall writing quality.

        Here is the blog post content:

        Title: ${blog.title}
        Content: ${blog.content}`) returns Review|error = @np:NaturalFunction external;
```

Note how the prompt refers to preceding parameters. These functions, thus, become a type-safe approach to share and reuse prompts.

The function can then be used in the code similar to any other function.

```ballerina
Review review = check reviewBlog(blog);
```

### Configuring the model

The model to use can be set either by configuration or by introducing a `context` parameter of type `np:Context`, with a `model` field of type `np:Model`, in the function.

1. Configuration

    Values need to be provided for the `defaultModelConfig` configurable value. E.g., add the relevant configuration in the Config.toml file as follows for Azure OpenAI:

    ```toml
    [ballerinax.np.defaultModelConfig]
    serviceUrl = "<SERVICE_URL>"
    deploymentId = "<DEPLOYMENT_ID>"
    apiVersion = "<API_VERSION>"
    connectionConfig.auth.apiKey = "<YOUR_API_KEY>"
    ```

2. Model in a parameter

    Alternatively, to have more control over the model for each function, a `context` parameter of type `np:Context`, with the `model` field of type `np:Model`, can be introduced in the function.

    ```ballerina
    public isolated function reviewBlog(
        Blog blog,
        np:Context context,
        np:Prompt prompt = `You are an expert content reviewer for a blog site that 
            categorizes posts under the following categories: ${categories}

            ...

            Here is the blog post content:

            Title: ${blog.title}
            Content: ${blog.content}`) returns Review|error = @np:NaturalFunction external;
    ```


The `ballerinax/np` module provides implementations of `np:Model` for different LLM providers: 

- `np:OpenAIModel` for Open AI
- `np:AzureOpenAIModel` for Azure Open AI

A model of these types can be initialized and provided as an argument for the `model` parameter.

```ballerina
configurable string apiKey = ?;
configurable string serviceUrl = ?;
configurable string deploymentId = ?;
configurable string apiVersion = ?;


final np:Model azureOpenAIModel = check new np:AzureOpenAIModel({
       serviceUrl, connectionConfig: {auth: {apiKey}}}, deploymentId, apiVersion);

Review review = check reviewBlog(blog, {model: azureOpenAIModel});
```

## The `np:callLlm` function

The `np:callLlm` function is an alternative to defining a separate function with the `np:NaturalFunction` annotation.

The function accepts a prompt of type `np:Prompt` and optionally, an `np:Conext` value with the `model` field of type `np:Model`. If the model is not specified, it has to be configured via the `defaultModelConfig` configurable variable. The function is dependently-typed and uses the inferred typedesc parameter to construct the JSON schema for the required response format and bind the response data to the expected type.

```ballerina
// Where `blog` is in scope.
Review review = check np:callLlm(`You are an expert content reviewer for a blog site that 
            categorizes posts under the following categories: ${categories}

            ...

            Here is the blog post content:

            Title: ${blog.title}
            Content: ${blog.content}`, {model: azureOpenAIModel});
```
