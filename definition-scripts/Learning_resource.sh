curl -L -X POST '{{host}}/object/category/definition/v4/create' \
-H 'Content-Type: application/json' \
--data-raw '{
  "request": {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:learning-resource",
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
                "application/vnd.ekstep.ecml-archive",
                "application/vnd.ekstep.html-archive",
                "application/vnd.ekstep.h5p-archive",
                "application/pdf",
                "video/mp4",
                "video/webm"
              ]
            }
          }
        }
      }
    }
  }
}'