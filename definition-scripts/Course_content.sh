curl -L -X POST '{{host}}/object/category/definition/v4/create' \
-H 'Content-Type: application/json' \
--data-raw '{
  "request": {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:course",
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
                  "default": "Yes"
                },
                "autoBatch": {
                    "type": "string",
                    "enum": ["Yes","No"],
                    "default": "Yes"
                }
              },
              "default": {
                "enabled": "Yes",
                "autoBatch": "Yes"
              },
              "additionalProperties": false
            },
            "monitorable": {
              "type": "array",
              "items": {
                "type": "string",
                "enum": [
                  "progress-report",
                  "score-report"
                ]
              }
            },
            "credentials": {
              "type": "object",
              "properties": {
                "enabled": {
                  "type": "string",
                  "enum": [
                    "Yes",
                    "No"
                  ],
                  "default": "Yes"
                }
              },
              "default": {
                "enabled": "Yes"
              },
              "additionalProperties": false
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