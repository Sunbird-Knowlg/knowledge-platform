curl -L -X POST '{{host}}/object/category/definition/v4/create' \
-H 'Content-Type: application/json' \
--data-raw '{
    "request": {
      "objectCategoryDefinition": {
        "categoryId": "obj-cat:course",
        "targetObjectType": "Collection",
        "objectMetadata": {
          "config": {
              "frameworkMetadata": {
                "orgFWType": [
                  "K-12",
                  "TPD"
              ],
              "targetFWType": [
                  "K-12"
              ]
            },
            "sourcingSettings": {
                "collection": {
                    "maxDepth": 4,
                    "objectType": "Collection",
                    "primaryCategory": "Course",
                    "isRoot": true,
                    "iconClass": "fa fa-book",
                    "children": {},
                    "hierarchy": {
                        "level1": {
                            "name": "Course Unit",
                            "type": "Unit",
                            "mimeType": "application/vnd.ekstep.content-collection",
                            "contentType": "CourseUnit",
                            "primaryCategory": "Course Unit",
                            "iconClass": "fa fa-folder-o",
                            "children": {
                                "Content": []
                            }
                        },
                        "level2": {
                            "name": "Course Unit",
                            "type": "Unit",
                            "mimeType": "application/vnd.ekstep.content-collection",
                            "contentType": "CourseUnit",
                            "primaryCategory": "Course Unit",
                            "iconClass": "fa fa-folder-o",
                            "children": {
                                "Content": []
                            }
                        },
                        "level3": {
                            "name": "Course Unit",
                            "type": "Unit",
                            "mimeType": "application/vnd.ekstep.content-collection",
                            "contentType": "CourseUnit",
                            "primaryCategory": "Course Unit",
                            "iconClass": "fa fa-folder-o",
                            "children": {
                                "Content": []
                            }
                        },
                        "level4": {
                            "name": "Course Unit",
                            "type": "Unit",
                            "mimeType": "application/vnd.ekstep.content-collection",
                            "contentType": "CourseUnit",
                            "primaryCategory": "Course Unit",
                            "iconClass": "fa fa-folder-o",
                            "children": {
                                "Content": []
                            }
                        }
                    }
                }
            }              
          },
          "schema": {
            "properties": {
              "trackable": {
                "type": "object",
                "properties": {
                  "enabled": {
                    "type": "string",
                    "enum": [
                      "Yes",
                      "No"
                    ],
                    "default": "Yes"
                  },
                  "autoBatch": {
                    "type": "string",
                    "enum": [
                      "Yes",
                      "No"
                    ],
                    "default": "Yes"
                  }
                },
                "default": {
                  "enabled": "Yes",
                  "autoBatch": "Yes"
                },
                "additionalProperties": false
              },
              "monitorable": {
                "type": "array",
                "items": {
                  "type": "string",
                  "enum": [
                    "progress-report",
                    "score-report"
                  ]
                }
              },
              "credentials": {
                "type": "object",
                "properties": {
                  "enabled": {
                    "type": "string",
                    "enum": [
                      "Yes",
                      "No"
                    ],
                    "default": "Yes"
                  }
                },
                "default": {
                  "enabled": "Yes"
                },
                "additionalProperties": false
              },
              "userConsent": {
                "type": "string",
                "enum": [
                  "Yes",
                  "No"
                ],
                "default": "Yes"
              },
              "mimeType": {
                "type": "string",
                "enum": [
                    "application/vnd.ekstep.content-collection"
                ]
              }
            }
          }
        },
        "languageCode": [],
        "forms": {
            "create": {
                "templateName": "",
                "required": [],
                "properties": [
                    {
                        "name": "First Section",
                        "fields": [
                            {
                                "code": "appIcon",
                                "dataType": "text",
                                "description": "appIcon of the content",
                                "editable": true,
                                "inputType": "appIcon",
                                "label": "Icon",
                                "name": "Icon",
                                "placeholder": "Icon",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1 required"
                                },
                                "required": true,
                                "visible": true
                            },
                            {
                                "code": "name",
                                "dataType": "text",
                                "description": "Name of the content",
                                "editable": true,
                                "inputType": "text",
                                "label": "Title",
                                "name": "Name",
                                "placeholder": "Title",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1 required"
                                },
                                "required": true,
                                "visible": true,
                                "validations": [
                                    {
                                        "type": "max",
                                        "value": "120",
                                        "message": "Input is Exceeded"
                                    },
                                    {
                                        "type": "required",
                                        "message": "Title is required"
                                    }
                                ]
                            },
                            {
                                "code": "description",
                                "dataType": "text",
                                "description": "Description of the content",
                                "editable": true,
                                "inputType": "textarea",
                                "label": "Description",
                                "name": "Description",
                                "placeholder": "Description",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1"
                                },
                                "required": true,
                                "visible": true,
                                "validations": [
                                    {
                                        "type": "max",
                                        "value": "256",
                                        "message": "Input is Exceeded"
                                    }
                                ]
                            },
                            {
                                "code": "keywords",
                                "visible": true,
                                "editable": true,
                                "dataType": "list",
                                "name": "Keywords",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1 required"
                                },
                                "description": "Keywords for the content",
                                "inputType": "keywords",
                                "label": "Keywords",
                                "placeholder": "Enter Keywords",
                                "required": true,
                                "validations": []
                            }
                        ]
                    },
                    {
                        "name": "Second Section",
                        "fields": [
                            {
                                "code": "primaryCategory",
                                "dataType": "text",
                                "description": "Type",
                                "editable": false,
                                "renderingHints": {},
                                "inputType": "select",
                                "label": "Category",
                                "name": "Type",
                                "placeholder": "",
                                "required": true,
                                "visible": true,
                                "validations": []
                            },
                            {
                                "code": "additionalCategories",
                                "dataType": "list",
                                "depends": [
                                    "primaryCategory"
                                ],
                                "description": "Additonal Category of the Content",
                                "editable": true,
                                "inputType": "nestedselect",
                                "label": "Additional Category",
                                "name": "Additional Category",
                                "placeholder": "Select Additional Category",
                                "renderingHints": {},
                                "required": false,
                                "visible": true
                            }
                        ]
                    },
                    {
                        "name": "Organisation Framework Terms",
                        "fields": [
                            {
                                "code": "framework",
                                "visible": true,
                                "editable": true,
                                "dataType": "text",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1 required"
                                },
                                "description": "",
                                "label": "Course Type",
                                "required": true,
                                "name": "Framework",
                                "inputType": "framework",
                                "placeholder": "Select Course Type",
                                "output": "identifier",
                                "validations": [
                                    {
                                        "type": "required",
                                        "message": "Course Type is required"
                                    }
                                ]
                            },
                            {
                                "code": "subjectIds",
                                "visible": true,
                                "editable": true,
                                "dataType": "list",
                                "depends": [
                                    "framework"
                                ],
                                "sourceCategory": "subject",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1 required"
                                },
                                "description": "",
                                "label": "Subjects covered in the course",
                                "required": true,
                                "name": "Subject",
                                "inputType": "frameworkCategorySelect",
                                "placeholder": "Select Subject(s)",
                                "output": "identifier",
                                "validations": [
                                    {
                                        "type": "required",
                                        "message": "Subjects Taught is required"
                                    }
                                ]
                            },
                            {
                                "code": "topicsIds",
                                "visible": true,
                                "editable": true,
                                "dataType": "list",
                                "depends": [
                                    "framework",
                                    "subjectIds"
                                ],
                                "sourceCategory": "topic",
                                "renderingHints": {},
                                "name": "Topic",
                                "description": "Choose a Topics",
                                "inputType": "topicselector",
                                "label": "Topics covered in the course",
                                "placeholder": "Choose Topics",
                                "required": false,
                                "output": "identifier"
                            }
                        ]
                    },
                    {
                        "name": "Target Framework Terms",
                        "fields": [
                            {
                                "code": "audience",
                                "dataType": "list",
                                "description": "Audience",
                                "editable": true,
                                "inputType": "nestedselect",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1"
                                },
                                "label": "Audience Type",
                                "name": "Audience Type",
                                "placeholder": "Select Audience Type",
                                "required": false,
                                "visible": true,
                                "range": [
                                    "Student",
                                    "Teacher",
                                    "Parent",
                                    "Administrator"
                                ]
                            },
                            {
                                "code": "targetBoardIds",
                                "visible": true,
                                "depends": [],
                                "editable": true,
                                "dataType": "list",
                                "sourceCategory": "board",
                                "output": "identifier",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1 required"
                                },
                                "description": "Board",
                                "label": "Board/Syllabus of the audience",
                                "required": true,
                                "name": "Board/Syllabus",
                                "inputType": "select",
                                "placeholder": "Select Board/Syllabus",
                                "validations": [
                                    {
                                        "type": "required",
                                        "message": "Board is required"
                                    }
                                ]
                            },
                            {
                                "code": "targetMediumIds",
                                "visible": true,
                                "depends": [
                                    "targetBoardIds"
                                ],
                                "editable": true,
                                "dataType": "list",
                                "sourceCategory": "medium",
                                "output": "identifier",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1 required"
                                },
                                "description": "",
                                "label": "Medium(s) of the audience",
                                "required": true,
                                "name": "Medium",
                                "inputType": "nestedselect",
                                "placeholder": "Select Medium",
                                "validations": [
                                    {
                                        "type": "required",
                                        "message": "Medium is required"
                                    }
                                ]
                            },
                            {
                                "code": "targetGradeLevelIds",
                                "visible": true,
                                "depends": [
                                    "targetBoardIds",
                                    "targetMediumIds"
                                ],
                                "editable": true,
                                "dataType": "list",
                                "sourceCategory": "gradeLevel",
                                "output": "identifier",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1 required"
                                },
                                "description": "Class",
                                "label": "Class(es) of the audience",
                                "required": true,
                                "name": "Class",
                                "inputType": "nestedselect",
                                "placeholder": "Select Class",
                                "validations": [
                                    {
                                        "type": "required",
                                        "message": "Class is required"
                                    }
                                ]
                            },
                            {
                                "code": "targetSubjectIds",
                                "visible": true,
                                "depends": [
                                    "targetBoardIds",
                                    "targetMediumIds",
                                    "targetGradeLevelIds"
                                ],
                                "editable": true,
                                "dataType": "list",
                                "sourceCategory": "subject",
                                "output": "identifier",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1 required"
                                },
                                "description": "",
                                "label": "Subject(s) of the audience",
                                "required": true,
                                "name": "Subject",
                                "inputType": "nestedselect",
                                "placeholder": "Select Subject",
                                "validations": [
                                    {
                                        "type": "required",
                                        "message": "Subject is required"
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        "name": "Fourth Section",
                        "fields": [
                            {
                                "code": "author",
                                "dataType": "text",
                                "description": "Author of the content",
                                "editable": true,
                                "inputType": "text",
                                "label": "Author",
                                "name": "Author",
                                "placeholder": "Author",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1"
                                },
                                "required": false,
                                "visible": true
                            },
                            {
                                "code": "attributions",
                                "dataType": "text",
                                "description": "Attributions",
                                "editable": true,
                                "inputType": "text",
                                "label": "Attributions",
                                "name": "Attributions",
                                "placeholder": "Attributions",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1"
                                },
                                "required": false,
                                "visible": true
                            },
                            {
                                "code": "copyright",
                                "dataType": "text",
                                "description": "Copyright",
                                "editable": true,
                                "inputType": "text",
                                "label": "Copyright",
                                "name": "Copyright & year",
                                "placeholder": "Copyright",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1 required"
                                },
                                "required": true,
                                "visible": true,
                                "validations": [
                                    {
                                        "type": "required",
                                        "message": "Copyright is required"
                                    }
                                ]
                            },
                            {
                                "code": "copyrightYear",
                                "dataType": "number",
                                "description": "Year",
                                "editable": true,
                                "inputType": "text",
                                "label": "Copyright Year",
                                "name": "Copyright Year",
                                "placeholder": "Copyright Year",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1 required"
                                },
                                "required": true,
                                "visible": true,
                                "validations": [
                                    {
                                        "type": "required",
                                        "message": "Copyright Year is required"
                                    }
                                ]
                            },
                            {
                                "code": "license",
                                "dataType": "text",
                                "description": "license",
                                "editable": true,
                                "inputType": "select",
                                "label": "License",
                                "name": "license",
                                "placeholder": "Select License",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1 required"
                                },
                                "required": true,
                                "visible": true,
                                "defaultValue": "CC BY 4.0",
                                "validations": [
                                    {
                                        "type": "required",
                                        "message": "License is required"
                                    }
                                ]
                            }
                        ]
                    }
                ]
            },
            "search": {
                "templateName": "",
                "required": [],
                "properties": [
                    {
                        "code": "primaryCategory",
                        "dataType": "list",
                        "description": "Type",
                        "editable": true,
                        "default": [],
                        "renderingHints": {
                            "class": "sb-g-col-lg-1"
                        },
                        "inputType": "nestedselect",
                        "label": "Content Type(s)",
                        "name": "Type",
                        "placeholder": "Select ContentType",
                        "required": false,
                        "visible": true
                    },
                    {
                        "code": "board",
                        "visible": true,
                        "depends": [],
                        "editable": true,
                        "dataType": "list",
                        "renderingHints": {
                            "class": "sb-g-col-lg-1"
                        },
                        "description": "Board",
                        "label": "Board",
                        "required": false,
                        "name": "Board",
                        "inputType": "select",
                        "placeholder": "Select Board",
                        "output": "name"
                    },
                    {
                        "code": "medium",
                        "visible": true,
                        "depends": [
                            "board"
                        ],
                        "editable": true,
                        "dataType": "list",
                        "renderingHints": {
                            "class": "sb-g-col-lg-1"
                        },
                        "description": "",
                        "label": "Medium(s)",
                        "required": false,
                        "name": "Medium",
                        "inputType": "nestedselect",
                        "placeholder": "Select Medium",
                        "output": "name"
                    },
                    {
                        "code": "gradeLevel",
                        "visible": true,
                        "depends": [
                            "board",
                            "medium"
                        ],
                        "editable": true,
                        "default": "",
                        "dataType": "list",
                        "renderingHints": {
                            "class": "sb-g-col-lg-1"
                        },
                        "description": "Class",
                        "label": "Class(es)",
                        "required": false,
                        "name": "Class",
                        "inputType": "nestedselect",
                        "placeholder": "Select Class",
                        "output": "name"
                    },
                    {
                        "code": "subject",
                        "visible": true,
                        "depends": [
                            "board",
                            "medium",
                            "gradeLevel"
                        ],
                        "editable": true,
                        "default": "",
                        "dataType": "list",
                        "renderingHints": {
                            "class": "sb-g-col-lg-1"
                        },
                        "description": "",
                        "label": "Subject(s)",
                        "required": false,
                        "name": "Subject",
                        "inputType": "nestedselect",
                        "placeholder": "Select Subject",
                        "output": "name"
                    },
                    {
                        "code": "topic",
                        "visible": true,
                        "editable": true,
                        "dataType": "list",
                        "depends": [
                            "board",
                            "medium",
                            "gradeLevel",
                            "subject"
                        ],
                        "default": "",
                        "renderingHints": {
                            "class": "sb-g-col-lg-1"
                        },
                        "name": "Topic",
                        "description": "Choose a Topics",
                        "inputType": "topicselector",
                        "label": "Topic(s)",
                        "placeholder": "Choose Topics",
                        "required": false
                    }
                ]
            },
            "unitMetadata": {
                "templateName": "",
                "required": [],
                "properties": [
                    {
                        "name": "First Section",
                        "fields": [
                            {
                                "code": "name",
                                "dataType": "text",
                                "description": "Name of the content",
                                "editable": true,
                                "inputType": "text",
                                "label": "Title",
                                "name": "Title",
                                "placeholder": "Title",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1 required"
                                },
                                "required": true,
                                "visible": true,
                                "validations": [
                                    {
                                        "type": "max",
                                        "value": "120",
                                        "message": "Input is Exceeded"
                                    },
                                    {
                                        "type": "required",
                                        "message": "Title is required"
                                    }
                                ]
                            },
                            {
                                "code": "description",
                                "dataType": "text",
                                "description": "Description of the content",
                                "editable": true,
                                "inputType": "textarea",
                                "label": "Description",
                                "name": "Description",
                                "placeholder": "Description",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1"
                                },
                                "required": true,
                                "visible": true,
                                "validations": [
                                    {
                                        "type": "max",
                                        "value": "256",
                                        "message": "Input is Exceeded"
                                    }
                                ]
                            },
                            {
                                "code": "keywords",
                                "visible": true,
                                "editable": true,
                                "dataType": "list",
                                "name": "Keywords",
                                "renderingHints": {
                                    "class": "sb-g-col-lg-1"
                                },
                                "index": 3,
                                "description": "Keywords for the content",
                                "inputType": "keywords",
                                "label": "Keywords",
                                "placeholder": "Enter Keywords",
                                "required": true,
                                "validations": []
                            },
                            {
                                "code": "topic",
                                "visible": true,
                                "depends": [],
                                "editable": true,
                                "dataType": "list",
                                "renderingHints": {},
                                "name": "Topic",
                                "description": "Choose a Topics",
                                "index": 11,
                                "inputType": "topicselector",
                                "label": "Topics",
                                "placeholder": "Choose Topics",
                                "required": false,
                                "validations": []
                            }
                        ]
                    }
                ]
             }
          }
       }
    }
}'
