curl -L -X POST '{{host}}/object/category/definition/v4/create' \
-H 'Content-Type: application/json' \
--data-raw '{
  "request": {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:teacher-resource",
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
                "application/pdf",
                "application/vnd.ekstep.h5p-archive",
                "application/vnd.ekstep.html-archive",
                "video/mp4",
                "video/webm"
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