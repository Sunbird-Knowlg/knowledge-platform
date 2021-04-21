curl -L -X POST '{{host}}/object/category/definition/v4/create' \
-H 'Content-Type: application/json' \
--data-raw {
  "request": {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:playlist",
      "targetObjectType": "Collection",
      "objectMetadata": {
        "config": {},
        "schema": {
          "properties": {
           "visibility": {
            "type": "string",
            "enum": [
                "Default",
                "Parent",
                "Private"
            ],
            "default": "Private"
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
                  "default": "Yes"
                },
                "autoBatch": {
                  "type": "string",
                  "enum": [
                    "Yes",
                    "No"
                  ],
                  "default": "Yes"
                }
              },
              "default": {
                "enabled": "Yes",
                "autoBatch": "Yes"
              },
              "additionalProperties": false
            }
          }
        }
      }
    }
  }
}'
