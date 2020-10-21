curl --location --request POST '{{host}}/object/category/definition/v4/create' \
--header 'Content-Type: application/json' \
--data-raw '{
    "request": {
        "objectCategoryDefinition": {
            "categoryId": "obj-cat:certificate-template",
            "targetObjectType": "Asset",
            "objectMetadata": {
                "config": {},
                "schema": {
                    "properties": {
                        "issuer": {
                            "type": "object"
                        },
                        "signatoryList": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "image": {
                                        "type": "string"
                                    },
                                    "name": {
                                        "type": "string"
                                    },
                                    "id": {
                                        "type": "string"
                                    },
                                    "designation": {
                                        "type": "string"
                                    }
                                }
                            }
                        },
                        "logos": {
                            "type": "array",
                            "items": {
                                "type": "string"
                            }
                        },
                        "certType": {
                            "type": "string",
                            "enum": [
                              "cert template layout",
                              "cert template"
                            ]
                        },
                        "data": {
                            "type": "object"
                        }
                    }
                }
            }
        }
    }
}'