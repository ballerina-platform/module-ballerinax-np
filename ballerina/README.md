# Overview

This library module provides implementations of `ballerina/np:ModelProvider` to use explicitly with natural expressions.

- [OpenAI](#openai)
- [Azure OpenAI](#azure-openai)

## Initializing the model

### OpenAI

```ballerina
import ballerinax/np.openai;

configurable string? serviceUrl = ();
configurable string model = ?;
configurable string token = ?;

final openai:ModelProvider openAI = check new ({
        connectionConfig: {
            auth: {
                token
            }
        },
        serviceUrl
    }, 
    model
);
```

Specify values for the configurable variables in the Config.toml file.

```toml
serviceUrl = "<SERVICE_URL>"
model = "<MODEL>"
token = "<TOKEN>"
```

### Azure OpenAI

```ballerina
import ballerinax/np.azure.openai as azureOpenAI;

configurable string serviceUrl = ?;
configurable string deploymentId = ?;
configurable string apiVersion = ?;
configurable string apiKey = ?;

final azureOpenAI:ModelProvider azureOpenAIModel = check new (
    {
        serviceUrl, 
        connectionConfig: {
            auth: {
                apiKey
            }
        }
    },
    deploymentId, 
    apiVersion
);
```

Specify values for the configurable variables in the Config.toml file.

```toml
serviceUrl = "<SERVICE_URL>"
deploymentId = "<DEPLOYMENT_ID>"
apiVersion = "<API_VERSION>"
connectionConfig.auth.apiKey = "<YOUR_API_KEY>"
```

## Using the model

A model of these types can be used in a natural expression.

```ballerina
import ballerina/io;
import ballerina/np;
import ballerinax/np.openai;

configurable string? serviceUrl = ();
configurable string model = ?;
configurable string token = ?;

type Author record {| 
    string name;
    string dateOfBirth;
    record {|
        string name;
        string yearOfPublication;
        string genre;
    |}[] works;
|};

isolated function getAuthorDetails(string month, int count, np:ModelProvider model) 
        returns Author|error => natural (model) {
    Who is a popular author born in ${month}? What are their ${count} most popular works?
};

public function main() returns error? {
    openai:ModelProvider openAI = check new ({
            connectionConfig: {
                auth: {
                    token
                }
            },
            serviceUrl
        }, 
        model
    );

    Author author = check getAuthorDetails("July", 5, openAI);
    io:println("Name: ", author.name);
    io:println("Date of Birth: ", author.dateOfBirth);
    foreach var work in author.works {
        io:println("Title: ", work.name);
        io:println("Year of Publication: ", work.yearOfPublication);
        io:println("Genre: ", work.genre);
    }
}
```
