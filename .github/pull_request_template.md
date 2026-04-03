## Jira Ticket
<!-- Link the Jira ticket this PR addresses: https://project.atlassian.net/browse/KNOWLG-XXXX -->

## Summary
<!-- Describe the change and which issue is fixed. Include motivation, context, and any dependencies required. -->

## Type of Change
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Refactoring (no functional change)
- [ ] Documentation update

## How Has This Been Tested?
<!-- Describe the tests run to verify your changes. Provide reproduction steps and test configuration details. -->

- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manually tested

**Test Configuration:**
- Java: 11 (Temurin)
- Scala: 2.13.12
- Play Framework: 3.0.5
- Apache Pekko: 1.0.3

## Checklist
- [ ] My code follows the style guidelines of this project (`scalafmt` passes locally)
- [ ] I have performed a self-review of my own code
- [ ] I have commented my code where logic is non-obvious
- [ ] I have made corresponding changes to documentation if needed
- [ ] My changes generate no new compiler warnings
- [ ] I have added/updated tests that prove my fix works or my feature is correct
- [ ] All new and existing unit tests pass locally (`mvn test -pl <module>`)
- [ ] Any dependent changes have been merged and published in downstream modules
- [ ] SonarCloud quality gate passes (check PR comment after CI completes)
- [ ] Code coverage is maintained at or above 80%
