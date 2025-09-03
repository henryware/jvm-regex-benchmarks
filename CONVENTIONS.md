## Scala Style
- Use scala-cli as build tool
- Use munit for tests
- Use scalacheck in tests where appropriate
- indent 4 characters
- indent.callSite 4 characters

## Meaningful Names
- Variables, functions, and classes should reveal their purpose
- Names should explain why something exists and how it's used
- Avoid abbreviations unless they're universally understood

## Smart Comments
- Don't comment on what the code does - make the code self-documenting
- Use comments to explain why something is done a certain way
- Document APIs, complex algorithms, and non-obvious side effects
- Don't use comments where not useful

## Single Responsibility
- Each function should do exactly one thing
- Functions should be small and focused
- If a function needs a comment to explain what it does, consider
  spliting it

## DRY (Don't Repeat Yourself)
- Extract repeated code into reusable functions
- Share common logic through proper abstraction
- Maintain single sources of truth

## Clean Structure
- Keep related code together
- Organize code in a logical hierarchy
- Use consistent file and folder naming conventions

## Encapsulation
- Hide implementation details
- Expose clear interfaces
- Move nested conditionals into well-named functions

## Code Quality Maintenance
- Refactor continuously
- Fix technical debt early
- Leave code cleaner than you found it
