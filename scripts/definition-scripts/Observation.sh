curl -L -X POST '{{host}}/object/category/definition/v4/create' \
-H 'Content-Type: application/json' \
--data-raw '{
  "request": {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:observation",
      "targetObjectType": "QuestionSet",
      "objectMetadata": {
        "config": {},
        "schema": {
          "properties": {
            "mimeType": {
              "type": "string",
              "enum": [
                "application/vnd.sunbird.questionset"
              ]
            },
            "allowBranching": {
              "type": "string",
              "enum": [
                "Yes",
                "No"
              ],
              "default": "Yes"
            },
            "audience": {
              "type": "array",
              "items": {
                "type": "string",
                "enum": [
                  "Education Official",
                  "School leaders (HMs)",
                  "Administrator",
                  "Teachers",
                  "Students",
                  "Parents",
                  "Others"
                ]
              }
            },
            "allowScoring": {
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