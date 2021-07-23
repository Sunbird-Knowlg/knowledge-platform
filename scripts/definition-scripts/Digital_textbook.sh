curl --location --request POST '{{host}}/object/category/definition/v4/create' \
--header 'Content-Type: application/json' \
--data-raw '{
  "request": {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:digital-textbook",
      "targetObjectType": "Collection",
      "objectMetadata": {
        "config": {},
        "schema": {
          "properties": {
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
              "default": "Yes"
            }
          }
        }
      }
    }
  }
}'

