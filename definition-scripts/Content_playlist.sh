curl -L -X POST '{{host}}/object/category/definition/v4/create' \
-H 'Content-Type: application/json' \
--data-raw '{
  "request": {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:content-playlist",
      "targetObjectType": "Collection",
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
            }
          }
        }
      }
    }
  }
}'