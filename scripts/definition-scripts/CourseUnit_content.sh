curl -L -X POST '{{host}}/object/category/definition/v4/create' \
-H 'Content-Type: application/json' \
--data-raw '{
  "request": {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:course-unit",
      "targetObjectType": "Content",
      "objectMetadata": {
        "config": {},
        "schema": {}
      }
    }
  }
}'