// Tests for lang version.

type Blog record {
    string title;
    string content;
};

function rateBlog(Blog blog, Prompt prompt = `Rate this blog out of 10.
        Title: ${blog.title}
        Content: ${blog.content}`) returns int = @LlmCall external;