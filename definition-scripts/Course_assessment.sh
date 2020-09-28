curl -L -X POST '{{host}}/object/category/definition/v4/create' \
-H 'Content-Type: application/json' \
--data-raw '{
  "request": {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:course-assessment",
      "targetObjectType": "Content",
      "objectMetadata": {
        "config": {},
        "schema": {
          "properties": {
            "audience": {
              "type": "array",
              "items": {
                "type": "string",
                "enum": [
                  "Student",
                  "Teacher",
                  "Administrator"
                ]
              }
            },
            "mimeType": {
              "type": "string",
              "enum": [
                "application/vnd.ekstep.ecml-archive"
              ]
            },
            "trackable": {
              "type": "object",
              "properties": {
                "enabled": {
                  "type": "string",
                  "enum": [
                    "Yes",
                    "No"
                  ],
                  "default": "No"
                },
                "autoBatch": {
                  "type": "string",
                  "enum": [
                    "Yes",
                    "No"
                  ],
                  "default": "No"
                }
              },
              "additionalProperties": false
            }
          }
        }
      }
    }
  }
}'