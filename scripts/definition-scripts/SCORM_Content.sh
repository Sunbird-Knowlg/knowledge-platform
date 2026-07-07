curl -L -X POST '{{host}}/object/category/definition/v4/create' \
-H 'Content-Type: application/json' \
--data-raw '{
  "request": {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:scorm-content",
      "targetObjectType": "Content",
      "objectMetadata": {
        "config": {},
        "schema": {
          "properties": {
            "launchFile": {
              "type": "string"
            },
            "scormVersion": {
              "type": "string"
            },
            "scoList": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "identifier": { "type": "string" },
                  "title": { "type": "string" },
                  "href": { "type": "string" },
                  "parameters": { "type": "string" }
                }
              }
            }
          }
        }
      }
    }
  }
}'
