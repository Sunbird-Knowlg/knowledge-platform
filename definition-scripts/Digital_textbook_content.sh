curl --location --request POST '{{host}}/object/category/definition/v4/create' \
--header 'Content-Type: application/json' \
--data-raw '{
  "request": {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:digital-textbook",
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
                  "Teacher"
                ]
              },
              "default": [
                "Student"
              ]
            },
            "mimeType": {
              "type": "string",
              "enum": [
                "application/vnd.ekstep.content-collection"
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
              "default": {
                "enabled": "No",
                "autoBatch": "No"
              },
              "additionalProperties": false
            },
            "additionalCategories": {
              "type": "array",
              "items": {
                "type": "string",
                "default": "Textbook"
              }
            },
            "userConsent": {
              "type": "string",
              "enum": [
                "Yes",
                "No"
              ],
              "default": "No"
            }
          }
        }
      }
    }
  }
}'

