curl -L -X POST '{{host}}/object/category/definition/v4/create' \
-H 'Content-Type: application/json' \
--data-raw '{
  "request" : {
    "objectCategoryDefinition": {
      "categoryId": "obj-cat:learning-path",
      "targetObjectType": "Collection",
      "objectMetadata": {
        "config": {
          "frameworkMetadata": {
            "orgFWType": ["K-12", "TPD"],
            "targetFWType": []
          },
          "sourcingSettings": {
            "collection": {
              "maxDepth": 1,
              "objectType": "Collection",
              "primaryCategory": "Learning Path",
              "isRoot": true,
              "iconClass": "fa fa-road",
              "children": {},
              "hierarchy": {
                "level1": {
                  "name": "Level",
                  "type": "Unit",
                  "mimeType": "application/vnd.ekstep.content-collection",
                  "contentType": "Level",
                  "primaryCategory": "Level",
                  "iconClass": "fa fa-layer-group",
                  "children": {
                    "Content": ["Course"]
                  }
                }
              }
            }
          }
        },
        "schema": {
          "properties": {
            "mimeType": {
              "type": "string",
              "enum": ["application/vnd.ekstep.content-collection"]
            },
            "policy": {
              "type": "string",
              "enum": ["strict", "adaptive", "priorLearning"],
              "default": "strict"
            },
            "trackable": {
              "type": "object",
              "properties": {
                "enabled":   { "type": "string", "enum": ["Yes", "No"], "default": "Yes" },
                "autoBatch": { "type": "string", "enum": ["Yes", "No"], "default": "No" }
              },
              "default": { "enabled": "Yes", "autoBatch": "No" },
              "additionalProperties": false
            },
            "credentials": {
              "type": "object",
              "properties": {
                "enabled": { "type": "string", "enum": ["Yes", "No"], "default": "Yes" }
              },
              "default": { "enabled": "Yes" },
              "additionalProperties": false
            },
            "monitorable": {
              "type": "array",
              "items": { "type": "string", "enum": ["progress-report", "score-report"] }
            },
            "userConsent": {
              "type": "string",
              "enum": ["Yes", "No"],
              "default": "Yes"
            },
            "audience": {
              "type": "array",
              "items": {
                "type": "string",
                "enum": ["Student", "Teacher", "Administrator", "Parent", "Other"]
              },
              "default": ["Student"]
            }
          }
        }
      },
      "forms": {
        "create": {
          "templateName": "",
          "required": [],
          "properties": [
            {
              "name": "First Section",
              "fields": [
                {
                  "code": "name",
                  "dataType": "text",
                  "description": "Name of the learning path",
                  "editable": true,
                  "inputType": "text",
                  "label": "Title",
                  "name": "Name",
                  "placeholder": "Title",
                  "renderingHints": { "class": "sb-g-col-lg-1 required" },
                  "required": true,
                  "visible": true,
                  "validations": [
                    { "type": "maxLength", "value": "120", "message": "Input is Exceeded" },
                    { "type": "required", "message": "Title is required" }
                  ]
                },
                {
                  "code": "description",
                  "dataType": "text",
                  "description": "Description of the learning path",
                  "editable": true,
                  "inputType": "textarea",
                  "label": "Description",
                  "name": "Description",
                  "placeholder": "A path from starting skill to demonstrated outcome.",
                  "renderingHints": { "class": "sb-g-col-lg-1" },
                  "required": false,
                  "visible": true,
                  "validations": [
                    { "type": "maxLength", "value": "256", "message": "Input is Exceeded" }
                  ]
                },
                {
                  "code": "keywords",
                  "dataType": "list",
                  "description": "Keywords for the learning path",
                  "editable": true,
                  "inputType": "keywords",
                  "label": "Keywords",
                  "name": "Keywords",
                  "placeholder": "Enter keywords",
                  "renderingHints": { "class": "sb-g-col-lg-1" },
                  "required": false,
                  "visible": true
                },
                {
                  "code": "policy",
                  "dataType": "text",
                  "description": "How learners move through the levels of this path",
                  "editable": true,
                  "inputType": "select",
                  "label": "Consumption policy",
                  "name": "Policy",
                  "placeholder": "Select…",
                  "renderingHints": { "class": "sb-g-col-lg-1 required" },
                  "required": true,
                  "visible": true,
                  "range": ["strict", "adaptive", "priorLearning"],
                  "default": "strict",
                  "validations": [
                    { "type": "required", "message": "Consumption policy is required" }
                  ]
                }
              ]
            }
          ]
        },
        "update": {
          "templateName": "",
          "required": [],
          "properties": [
            {
              "name": "Basic information",
              "fields": [
                {
                  "code": "name",
                  "dataType": "text",
                  "editable": true,
                  "inputType": "text",
                  "label": "Title",
                  "name": "Name",
                  "placeholder": "Title",
                  "renderingHints": { "class": "sb-g-col-lg-1 required" },
                  "required": true,
                  "visible": true,
                  "validations": [
                    { "type": "maxLength", "value": "120", "message": "Input is Exceeded" },
                    { "type": "required", "message": "Title is required" }
                  ]
                },
                {
                  "code": "description",
                  "dataType": "text",
                  "editable": true,
                  "inputType": "textarea",
                  "label": "Description",
                  "name": "Description",
                  "placeholder": "A path from starting skill to demonstrated outcome.",
                  "renderingHints": { "class": "sb-g-col-lg-1" },
                  "required": false,
                  "visible": true
                },
                {
                  "code": "appIcon",
                  "dataType": "text",
                  "editable": true,
                  "inputType": "appIcon",
                  "label": "Icon",
                  "name": "Icon",
                  "renderingHints": { "class": "sb-g-col-lg-1" },
                  "required": false,
                  "visible": true
                }
              ]
            },
            {
              "name": "Consumption policy",
              "fields": [
                {
                  "code": "policy",
                  "dataType": "text",
                  "editable": true,
                  "inputType": "select",
                  "label": "Consumption policy",
                  "name": "Policy",
                  "placeholder": "Select…",
                  "renderingHints": { "class": "sb-g-col-lg-1 required" },
                  "required": true,
                  "visible": true,
                  "range": ["strict", "adaptive", "priorLearning"],
                  "default": "strict"
                }
              ]
            },
            {
              "name": "Audience and licensing",
              "fields": [
                {
                  "code": "audience",
                  "dataType": "list",
                  "editable": true,
                  "inputType": "nestedselect",
                  "label": "Audience",
                  "name": "Audience",
                  "placeholder": "Select audience",
                  "renderingHints": { "class": "sb-g-col-lg-1" },
                  "required": false,
                  "visible": true,
                  "range": ["Student", "Teacher", "Administrator", "Parent", "Other"]
                },
                {
                  "code": "author",
                  "dataType": "text",
                  "editable": true,
                  "inputType": "text",
                  "label": "Author",
                  "name": "Author",
                  "renderingHints": { "class": "sb-g-col-lg-1" },
                  "required": false,
                  "visible": true
                },
                {
                  "code": "copyright",
                  "dataType": "text",
                  "editable": true,
                  "inputType": "text",
                  "label": "Copyright",
                  "name": "Copyright",
                  "renderingHints": { "class": "sb-g-col-lg-1" },
                  "required": false,
                  "visible": true
                },
                {
                  "code": "copyrightYear",
                  "dataType": "number",
                  "editable": true,
                  "inputType": "text",
                  "label": "Copyright year",
                  "name": "CopyrightYear",
                  "renderingHints": { "class": "sb-g-col-lg-1" },
                  "required": false,
                  "visible": true
                },
                {
                  "code": "license",
                  "dataType": "text",
                  "editable": true,
                  "inputType": "select",
                  "label": "License",
                  "name": "License",
                  "renderingHints": { "class": "sb-g-col-lg-1" },
                  "required": false,
                  "visible": true
                }
              ]
            }
          ]
        },
        "unitMetadata": {
          "templateName": "",
          "required": [],
          "properties": [
            {
              "name": "Level information",
              "description": "Title and description shown to learners for this level.",
              "fields": [
                {
                  "code": "name",
                  "dataType": "text",
                  "editable": true,
                  "inputType": "text",
                  "label": "Title",
                  "name": "Name",
                  "placeholder": "Level title",
                  "renderingHints": { "class": "sb-g-col-lg-1 required" },
                  "required": true,
                  "visible": true,
                  "validations": [
                    { "type": "maxLength", "value": "120", "message": "Input is Exceeded" },
                    { "type": "required", "message": "Title is required" }
                  ]
                },
                {
                  "code": "description",
                  "dataType": "text",
                  "editable": true,
                  "inputType": "textarea",
                  "label": "Description",
                  "name": "Description",
                  "placeholder": "What learners build in this level",
                  "renderingHints": { "class": "sb-g-col-lg-1" },
                  "required": false,
                  "visible": true
                }
              ]
            }
          ]
        },
        "childMetadata": {
          "templateName": "",
          "required": [],
          "properties": [
            {
              "name": "First Section",
              "fields": [
                {
                  "code": "name",
                  "dataType": "text",
                  "editable": false,
                  "inputType": "text",
                  "label": "Title",
                  "name": "Name",
                  "renderingHints": { "class": "sb-g-col-lg-1" },
                  "required": true,
                  "visible": true
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
              "code": "status",
              "dataType": "list",
              "editable": true,
              "inputType": "nestedselect",
              "label": "Status",
              "name": "Status",
              "visible": true,
              "range": ["Draft", "Review", "Live"]
            }
          ]
        },
        "publishchecklist": {
          "templateName": "",
          "required": [],
          "properties": [
            {
              "name": "Publish checklist",
              "fields": [
                {
                  "code": "checklist",
                  "dataType": "list",
                  "inputType": "checkbox",
                  "label": "Learning path review checklist",
                  "name": "Checklist",
                  "visible": true,
                  "range": [
                    "Prior assessment is a question-set-only course (when policy requires it)",
                    "Outcome assessment is a question-set-only course",
                    "Every level has at least one course and at least one skill",
                    "Level skills are within the prior assessment'"'"'s skill scope",
                    "All linked courses carry skill tags",
                    "No duplicate courses across the path",
                    "Consumption policy is appropriate for the audience"
                  ]
                }
              ]
            }
          ]
        }
      }
    }
  }
}'
