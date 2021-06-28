#!/usr/bin/env bash
curl -L -X POST '{{host}}/object/category/definition/v4/create' \
-H 'Content-Type: application/json' \
--data-raw '{
  "request": {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:multiple-choice-question",
      "targetObjectType": "Question",
      "objectMetadata": {
        "config": {},
        "schema": {}
      }
    }
  }
}'