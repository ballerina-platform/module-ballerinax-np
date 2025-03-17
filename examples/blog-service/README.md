# Blog service

A sample blog service supporting the following functionality:

1. Retrieve posts by category, sorted by a rating identified when the blog post is created - implemented entirely in Ballerina.

2. Submit a new post

    a. LLM call to identify the most suitable category (from a specified list) and a rating out of 1 - 10 (based on a defined criteria) - [implemented as a natural function](./review_blog.bal) with the requirement specified in natural language

    b. If there is a suitable category and the rating is greater than 3, accept the submission and persist the data. If not, reject the post - implemented entirely in Ballerina.
