curl --location --request POST '{{host}}/object/category/definition/v4/create' \
--header 'Content-Type: application/json' \
--data-raw '{
    "request":{
        "objectCategoryDefinition":{
            "categoryId": "obj-cat:textbook-unit",
            "targetObjectType": "Collection",
            "objectMetadata":{
                "config":{},
                "schema":{
                    "properties": {
                        "trackable": {
                        "type": "object",
                        "properties": {
                            "enabled": {
                                "type": "string",
                                "enum": ["Yes","No"],
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

