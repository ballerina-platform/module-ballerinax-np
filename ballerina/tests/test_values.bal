
type Blog record {
    string title;
    string content;
};

type Review record {|
    int rating;
    string comment;
|};

final readonly & Blog blog1 = {
    // Generated.
    title: "Tips for Growing a Beautiful Garden",
    content: string `Spring is the perfect time to start your garden. 
        Begin by preparing your soil with organic compost and ensure proper drainage. 
        Choose plants suitable for your climate zone, and remember to water them regularly. 
        Don't forget to mulch to retain moisture and prevent weeds.`
};

final readonly & Blog blog2 = {
    // Generated.
    title: "Essential Tips for Sports Performance",
    content: string `Success in sports requires dedicated preparation and training.
        Begin by establishing a proper warm-up routine and maintaining good form.
        Choose the right equipment for your sport, and stay consistent with training.
        Don't forget to maintain proper hydration and nutrition for optimal performance.`
};

final readonly & Review review2 = {
    rating: 8,
    comment: "Talks about essential aspects of sports performance including warm-up, form, equipment, and nutrition."
};

final string expectedPromptStringForRateBlog = string `Rate this blog out of 10.
        Title: ${blog1.title}
        Content: ${blog1.content}.  
        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```json"} and ${"```"}
        
        Schema:
        {"type":"integer"}`;

final string expectedPromptStringForRateBlog2 = string `Please rate this blog out of 10.
        Title: ${blog2.title}
        Content: ${blog2.content}.  
        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```json"} and ${"```"}
        
        Schema:
        {"$schema":"https://json-schema.org/draft/2020-12/schema", "type":"object", "properties":{"rating":{"type":"integer"}, "comment":{"type":"string"}}, "required":["rating", "comment"]}`;

final string expectedPromptStringForRateBlog3 = string `What is 1 + 1?.  
        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```json"} and ${"```"}
        
        Schema:
        {"type":"boolean"}`;

final string expectedPromptStringForRateBlog4 = string `Tell me name and the age of the top 10 world class cricketers.  
        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```json"} and ${"```"}
        
        Schema:
        {"$schema":"https://json-schema.org/draft/2020-12/schema", "type":"array", "items":{"$schema":"https://json-schema.org/draft/2020-12/schema", "type":"object", "properties":{"name":{"type":"string"}}, "required":["name"]}}`;

final string expectedPromptStringForBalProgram = string `What's the output of the Ballerina code below?

    ${"```"}ballerina
    import ballerina/io;

    public function main() {
        int x = 10;
        int y = 20;
        io:println(x + y);
    \}
    ${"```"}.  
        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```json"} and ${"```"}
        
        Schema:
        {"type":"integer"}`;

final string expectedPromptStringForCountry = string `Which country is known as the pearl of the Indian Ocean?.  
        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```json"} and ${"```"}
        
        Schema:
        {"type":"string"}`;

