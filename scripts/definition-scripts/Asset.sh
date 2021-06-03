curl --location --request POST '{{host}}/object/category/definition/v4/create' \
--header 'Content-Type: application/json' \
--data-raw '{
    "request":{
        "objectCategoryDefinition":{
            "categoryId": "obj-cat:asset",
            "targetObjectType": "Asset",
            "objectMetadata":{
                "config":{},
                "schema":{}
            }
        }
    }
}'

