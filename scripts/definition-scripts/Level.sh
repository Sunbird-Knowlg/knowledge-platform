curl -L -X POST '{{host}}/object/category/definition/v4/create' \
-H 'Content-Type: application/json' \
--data-raw '{
  "request": {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:level",
      "targetObjectType": "Collection",
      "objectMetadata": {
        "config": {
          "sourcingSettings": {
            "collection": {
              "objectType": "Collection",
              "primaryCategory": "Level",
              "isRoot": false,
              "iconClass": "fa fa-layer-group",
              "children": { "Content": ["Course"] }
            }
          }
        },
        "schema": {
          "properties": {
            "mimeType": {
              "type": "string",
              "enum": ["application/vnd.ekstep.content-collection"]
            },
            "visibility": {
              "type": "string",
              "enum": ["Parent"],
              "default": "Parent"
            }
          }
        }
      },
      "forms": {}
    }
  }
}'
