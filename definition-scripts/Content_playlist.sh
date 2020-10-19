curl -L -X POST '{{host}}/object/category/definition/v4/create' \
-H 'Content-Type: application/json' \
--data-raw '{
  "request": {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:content-playlist",
      "targetObjectType": "Collection",
      "objectMetadata": {
        "config": {},
        "schema": {}
      }
    }
  }
}'