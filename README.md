# KnowHOW Processors

Code of data collectors organized in 5 different images.
Every image is optional to deploy, based on data sources use, a knowHOW imstance can use one or all processors.

This is developed using Java 17 with Springboot 3.xx

## Security Requirements (Mandatory)

This repository uses GitGuardian via pre-commit hooks to scans your code before every commit.

Run once after cloning:
```
pip install pre-commit
pre-commit install
