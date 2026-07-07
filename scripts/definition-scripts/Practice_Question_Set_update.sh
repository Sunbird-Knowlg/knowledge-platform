#!/usr/bin/env bash
curl -L -X PATCH '{{host}}/object/category/definition/v4/update/obj-cat:practice-question-set_questionset_all' \
-H 'Content-Type: application/json' \
--data-raw '{
    "request": {
        "objectCategoryDefinition": {
            "objectMetadata": {
                "config": {
                    "sourcingSettings": {
                        "collection": {
                            "maxDepth": 1,
                            "objectType": "QuestionSet",
                            "primaryCategory": "Practice Question Set",
                            "isRoot": true,
                            "iconClass": "fa fa-book",
                            "children": {},
                            "hierarchy": {
                                "level1": {
                                    "name": "Section",
                                    "type": "Unit",
                                    "mimeType": "application/vnd.sunbird.questionset",
                                    "primaryCategory": "Practice Question Set",
                                    "iconClass": "fa fa-folder-o",
                                    "children": {
                                        "Question": [
                                            "Multiple Choice Question",
                                            "Subjective Question"
                                        ]
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
                            "enum": [
                                "application/vnd.sunbird.questionset"
                            ]
                        }
                    }
                }
            },
            "forms": {
                "childMetadata": {
                    "templateName": "",
                    "required": [],
                    "properties": [
                        {
                            "code": "name",
                            "dataType": "text",
                            "description": {
                                "en": "Name of the content",
                                "ar": "اسم المحتوى",
                                "fr": "Nom du contenu",
                                "pt": "Nome do conteúdo"
                            },
                            "editable": true,
                            "inputType": "text",
                            "label": {
                                "en": "Title",
                                "ar": "عنوان",
                                "fr": "Titre",
                                "pt": "Título"
                            },
                            "name": {
                                "en": "Title",
                                "ar": "عنوان",
                                "fr": "Titre",
                                "pt": "Título"
                            },
                            "placeholder": {
                                "en": "Title",
                                "ar": "عنوان",
                                "fr": "Titre",
                                "pt": "Título"
                            },
                            "renderingHints": {
                                "class": "sb-g-col-lg-1 required"
                            },
                            "required": true,
                            "visible": true,
                            "validations": [
                                {
                                    "type": "max",
                                    "value": "100",
                                    "message": {
                                        "en": "Input is Exceeded",
                                        "ar": "تم تجاوز الحد",
                                        "fr": "La saisie est dépassée",
                                        "pt": "A entrada foi excedida"
                                    }
                                },
                                {
                                    "type": "required",
                                    "message": {
                                        "en": "Title is required",
                                        "ar": "العنوان مطلوب",
                                        "fr": "Le titre est requis",
                                        "pt": "O título é obrigatório"
                                    }
                                }
                            ]
                        },
                        {
                            "code": "board",
                            "default": "",
                            "visible": true,
                            "editable": false,
                            "dataType": "text",
                            "renderingHints": {
                                "class": "sb-g-col-lg-1"
                            },
                            "description": {
                                "en": "Board",
                                "ar": "المجلس",
                                "fr": "Conseil",
                                "pt": "Conselho"
                            },
                            "label": {
                                "en": "Board/Syllabus",
                                "ar": "المجلس/المنهج",
                                "fr": "Conseil/Programme",
                                "pt": "Conselho/Currículo"
                            },
                            "required": false,
                            "name": {
                                "en": "Board/Syllabus",
                                "ar": "المجلس/المنهج",
                                "fr": "Conseil/Programme",
                                "pt": "Conselho/Currículo"
                            },
                            "inputType": "select",
                            "placeholder": {
                                "en": "Select Board/Syllabus",
                                "ar": "اختر المجلس/المنهج",
                                "fr": "Sélectionner Conseil/Programme",
                                "pt": "Selecionar Conselho/Currículo"
                            }
                        },
                        {
                            "code": "medium",
                            "visible": true,
                            "editable": true,
                            "default": "",
                            "dataType": "list",
                            "renderingHints": {
                                "class": "sb-g-col-lg-1"
                            },
                            "description": {
                                "en": "",
                                "ar": "",
                                "fr": "",
                                "pt": ""
                            },
                            "label": {
                                "en": "Medium",
                                "ar": "الوسيط",
                                "fr": "Médium",
                                "pt": "Médio"
                            },
                            "required": false,
                            "name": {
                                "en": "Medium",
                                "ar": "الوسيط",
                                "fr": "Médium",
                                "pt": "Médio"
                            },
                            "inputType": "select",
                            "placeholder": {
                                "en": "Select Medium",
                                "ar": "اختر الوسيط",
                                "fr": "Sélectionner le Médium",
                                "pt": "Selecionar Médio"
                            }
                        },
                        {
                            "code": "gradeLevel",
                            "visible": true,
                            "editable": true,
                            "default": "",
                            "dataType": "list",
                            "renderingHints": {
                                "class": "sb-g-col-lg-1"
                            },
                            "description": {
                                "en": "Class",
                                "ar": "الصف",
                                "fr": "Classe",
                                "pt": "Classe"
                            },
                            "label": {
                                "en": "Class",
                                "ar": "الصف",
                                "fr": "Classe",
                                "pt": "Classe"
                            },
                            "required": false,
                            "name": {
                                "en": "Class",
                                "ar": "الصف",
                                "fr": "Classe",
                                "pt": "Classe"
                            },
                            "inputType": "select",
                            "placeholder": {
                                "en": "Select Class",
                                "ar": "اختر الصف",
                                "fr": "Sélectionner la Classe",
                                "pt": "Selecionar a Classe"
                            }
                        },
                        {
                            "code": "subject",
                            "visible": true,
                            "editable": true,
                            "default": "",
                            "dataType": "list",
                            "renderingHints": {
                                "class": "sb-g-col-lg-1"
                            },
                            "description": {
                                "en": "",
                                "ar": "",
                                "fr": "",
                                "pt": ""
                            },
                            "label": {
                                "en": "Subject",
                                "ar": "المادة",
                                "fr": "Matière",
                                "pt": "Disciplina"
                            },
                            "required": false,
                            "name": {
                                "en": "Subject",
                                "ar": "المادة",
                                "fr": "Matière",
                                "pt": "Disciplina"
                            },
                            "inputType": "select",
                            "placeholder": {
                                "en": "Select Subject",
                                "ar": "اختر المادة",
                                "fr": "Sélectionner la Matière",
                                "pt": "Selecionar a Disciplina"
                            }
                        },
                        {
                            "code": "maxScore",
                            "dataType": "number",
                            "description": {
                                "en": "Marks",
                                "ar": "الدرجات",
                                "fr": "Notes",
                                "pt": "Notas"
                            },
                            "editable": true,
                            "inputType": "text",
                            "label": {
                                "en": "Marks:",
                                "ar": "الدرجات:",
                                "fr": "Notes :",
                                "pt": "Notas:"
                            },
                            "name": {
                                "en": "Marks",
                                "ar": "الدرجات",
                                "fr": "Notes",
                                "pt": "Notas"
                            },
                            "placeholder": {
                                "en": "Marks",
                                "ar": "الدرجات",
                                "fr": "Notes",
                                "pt": "Notas"
                            },
                            "tooltip": "Provide marks of this question.",
                            "renderingHints": {
                                "class": "sb-g-col-lg-1 required"
                            },
                            "validations": [
                                {
                                    "type": "pattern",
                                    "value": "^[1-9]{1}[0-9]*$",
                                    "message": {
                                        "en": "Input should be numeric",
                                        "ar": "يجب أن تكون المدخلات رقمية",
                                        "fr": "La saisie doit être numérique",
                                        "pt": "A entrada deve ser numérica"
                                    }
                                },
                                {
                                    "type": "required",
                                    "message": {
                                        "en": "Marks is required",
                                        "ar": "الدرجات مطلوبة",
                                        "fr": "Les notes sont requises",
                                        "pt": "As notas são obrigatórias"
                                    }
                                }
                            ]
                        }
                    ]
                },
                "create": {
                    "templateName": "",
                    "required": [],
                    "properties": [
                        {
                            "name": {
                                "en": "Basic details",
                                "ar": "التفاصيل الأساسية",
                                "fr": "Détails de base",
                                "pt": "Detalhes básicos"
                            },
                            "fields": [
                                {
                                    "code": "appIcon",
                                    "name": {
                                        "en": "Icon",
                                        "ar": "رمز",
                                        "fr": "Icône",
                                        "pt": "Ícone"
                                    },
                                    "label": {
                                        "en": "Icon",
                                        "ar": "رمز",
                                        "fr": "Icône",
                                        "pt": "Ícone"
                                    },
                                    "placeholder": {
                                        "en": "Icon",
                                        "ar": "رمز",
                                        "fr": "Icône",
                                        "pt": "Ícone"
                                    },
                                    "description": {
                                        "en": "Icon for the question set",
                                        "ar": "رمز مجموعة الأسئلة",
                                        "fr": "Icône pour l'\''ensemble de questions",
                                        "pt": "Ícone para o conjunto de perguntas"
                                    },
                                    "dataType": "text",
                                    "inputType": "appIcon",
                                    "editable": true,
                                    "required": true,
                                    "visible": true,
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1 required"
                                    }
                                },
                                {
                                    "code": "name",
                                    "name": {
                                        "en": "Name",
                                        "ar": "الاسم",
                                        "fr": "Nom",
                                        "pt": "Nome"
                                    },
                                    "label": {
                                        "en": "Name",
                                        "ar": "الاسم",
                                        "fr": "Nom",
                                        "pt": "Nome"
                                    },
                                    "placeholder": {
                                        "en": "Name",
                                        "ar": "الاسم",
                                        "fr": "Nom",
                                        "pt": "Nome"
                                    },
                                    "description": {
                                        "en": "Name of the QuestionSet",
                                        "ar": "اسم مجموعة الأسئلة",
                                        "fr": "Nom de l'\''ensemble de questions",
                                        "pt": "Nome do conjunto de perguntas"
                                    },
                                    "dataType": "text",
                                    "inputType": "text",
                                    "editable": true,
                                    "required": true,
                                    "visible": true,
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1 required"
                                    },
                                    "validations": [
                                        {
                                            "type": "max",
                                            "value": "120",
                                            "message": {
                                                "en": "Input is Exceeded",
                                                "ar": "تم تجاوز الحد",
                                                "fr": "La saisie est dépassée",
                                                "pt": "A entrada foi excedida"
                                            }
                                        },
                                        {
                                            "type": "required",
                                            "message": {
                                                "en": "Name is required",
                                                "ar": "الاسم مطلوب",
                                                "fr": "Le nom est requis",
                                                "pt": "O nome é obrigatório"
                                            }
                                        }
                                    ]
                                },
                                {
                                    "code": "description",
                                    "name": {
                                        "en": "Description",
                                        "ar": "الوصف",
                                        "fr": "Description",
                                        "pt": "Descrição"
                                    },
                                    "label": {
                                        "en": "Description",
                                        "ar": "الوصف",
                                        "fr": "Description",
                                        "pt": "Descrição"
                                    },
                                    "placeholder": {
                                        "en": "Description",
                                        "ar": "الوصف",
                                        "fr": "Description",
                                        "pt": "Descrição"
                                    },
                                    "description": {
                                        "en": "Description of the content",
                                        "ar": "وصف المحتوى",
                                        "fr": "Description du contenu",
                                        "pt": "Descrição do conteúdo"
                                    },
                                    "dataType": "text",
                                    "inputType": "textarea",
                                    "editable": true,
                                    "required": true,
                                    "visible": true,
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1 required"
                                    },
                                    "validations": [
                                        {
                                            "type": "required",
                                            "message": {
                                                "en": "description is required",
                                                "ar": "الوصف مطلوب",
                                                "fr": "La description est requise",
                                                "pt": "A descrição é obrigatória"
                                            }
                                        }
                                    ]
                                },
                                {
                                    "code": "keywords",
                                    "name": {
                                        "en": "Keywords",
                                        "ar": "الكلمات المفتاحية",
                                        "fr": "Mots-clés",
                                        "pt": "Palavras-chave"
                                    },
                                    "label": {
                                        "en": "keywords",
                                        "ar": "الكلمات المفتاحية",
                                        "fr": "Mots-clés",
                                        "pt": "Palavras-chave"
                                    },
                                    "placeholder": {
                                        "en": "Enter Keywords",
                                        "ar": "أدخل الكلمات المفتاحية",
                                        "fr": "Entrer des mots-clés",
                                        "pt": "Inserir palavras-chave"
                                    },
                                    "description": {
                                        "en": "Keywords for the Question Set",
                                        "ar": "الكلمات المفتاحية لمجموعة الأسئلة",
                                        "fr": "Mots-clés pour l'\''ensemble de questions",
                                        "pt": "Palavras-chave para o conjunto de perguntas"
                                    },
                                    "dataType": "list",
                                    "inputType": "keywords",
                                    "editable": true,
                                    "required": false,
                                    "visible": true,
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1"
                                    }
                                },
                                {
                                    "code": "instructions",
                                    "name": {
                                        "en": "Instructions",
                                        "ar": "التعليمات",
                                        "fr": "Instructions",
                                        "pt": "Instruções"
                                    },
                                    "label": {
                                        "en": "Instructions",
                                        "ar": "التعليمات",
                                        "fr": "Instructions",
                                        "pt": "Instruções"
                                    },
                                    "placeholder": {
                                        "en": "Enter Instructions",
                                        "ar": "أدخل التعليمات",
                                        "fr": "Entrer les instructions",
                                        "pt": "Inserir instruções"
                                    },
                                    "description": {
                                        "en": "Instructions for the question set",
                                        "ar": "التعليمات لمجموعة الأسئلة",
                                        "fr": "Instructions pour l'\''ensemble de questions",
                                        "pt": "Instruções para o conjunto de perguntas"
                                    },
                                    "dataType": "text",
                                    "inputType": "richtext",
                                    "editable": true,
                                    "required": false,
                                    "visible": true,
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-2"
                                    },
                                    "validations": [
                                        {
                                            "type": "maxLength",
                                            "value": "500",
                                            "message": {
                                                "en": "Input is Exceeded",
                                                "ar": "تم تجاوز الحد",
                                                "fr": "La saisie est dépassée",
                                                "pt": "A entrada foi excedida"
                                            }
                                        }
                                    ]
                                },
                                {
                                    "code": "primaryCategory",
                                    "name": {
                                        "en": "Type",
                                        "ar": "النوع",
                                        "fr": "Type",
                                        "pt": "Tipo"
                                    },
                                    "label": {
                                        "en": "Type",
                                        "ar": "النوع",
                                        "fr": "Type",
                                        "pt": "Tipo"
                                    },
                                    "placeholder": {
                                        "en": "",
                                        "ar": "",
                                        "fr": "",
                                        "pt": ""
                                    },
                                    "description": {
                                        "en": "Type or Category of the Question Set",
                                        "ar": "نوع أو فئة مجموعة الأسئلة",
                                        "fr": "Type ou catégorie de l'\''ensemble de questions",
                                        "pt": "Tipo ou categoria do conjunto de perguntas"
                                    },
                                    "dataType": "text",
                                    "inputType": "text",
                                    "editable": false,
                                    "required": false,
                                    "visible": true,
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1"
                                    }
                                }
                            ]
                        },
                        {
                            "name": {
                                "en": "Framework details",
                                "ar": "تفاصيل الإطار",
                                "fr": "Détails du cadre",
                                "pt": "Detalhes do quadro"
                            },
                            "fields": [
                                {
                                    "code": "board",
                                    "name": {
                                        "en": "Board/Syllabus",
                                        "ar": "المجلس/المنهج",
                                        "fr": "Conseil/Programme",
                                        "pt": "Conselho/Currículo"
                                    },
                                    "label": {
                                        "en": "Board/Syllabus",
                                        "ar": "المجلس/المنهج",
                                        "fr": "Conseil/Programme",
                                        "pt": "Conselho/Currículo"
                                    },
                                    "placeholder": {
                                        "en": "Select Board/Syllabus",
                                        "ar": "اختر المجلس/المنهج",
                                        "fr": "Sélectionner Conseil/Programme",
                                        "pt": "Selecionar Conselho/Currículo"
                                    },
                                    "description": {
                                        "en": "Board or Syallbus of the Question Set",
                                        "ar": "المجلس أو المنهج لمجموعة الأسئلة",
                                        "fr": "Conseil ou programme de l'\''ensemble de questions",
                                        "pt": "Conselho ou currículo do conjunto de perguntas"
                                    },
                                    "default": "",
                                    "dataType": "text",
                                    "inputType": "select",
                                    "editable": true,
                                    "required": true,
                                    "visible": true,
                                    "depends": [],
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1 required"
                                    },
                                    "validations": [
                                        {
                                            "type": "required",
                                            "message": {
                                                "en": "Board is required",
                                                "ar": "المجلس مطلوب",
                                                "fr": "Le conseil est requis",
                                                "pt": "O conselho é obrigatório"
                                            }
                                        }
                                    ]
                                },
                                {
                                    "code": "medium",
                                    "name": {
                                        "en": "Medium",
                                        "ar": "الوسيط",
                                        "fr": "Médium",
                                        "pt": "Médio"
                                    },
                                    "label": {
                                        "en": "Medium",
                                        "ar": "الوسيط",
                                        "fr": "Médium",
                                        "pt": "Médio"
                                    },
                                    "placeholder": {
                                        "en": "Select Medium",
                                        "ar": "اختر الوسيط",
                                        "fr": "Sélectionner le Médium",
                                        "pt": "Selecionar o Médio"
                                    },
                                    "description": {
                                        "en": "Medium of Instruction for the Question Set",
                                        "ar": "وسيط التعليم لمجموعة الأسئلة",
                                        "fr": "Médium d'\''instruction pour l'\''ensemble de questions",
                                        "pt": "Médio de instrução para o conjunto de perguntas"
                                    },
                                    "default": "",
                                    "dataType": "list",
                                    "inputType": "select",
                                    "editable": true,
                                    "required": true,
                                    "visible": true,
                                    "depends": [
                                        "board"
                                    ],
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1 required"
                                    },
                                    "validations": [
                                        {
                                            "type": "required",
                                            "message": {
                                                "en": "Medium is required",
                                                "ar": "الوسيط مطلوب",
                                                "fr": "Le médium est requis",
                                                "pt": "O médio é obrigatório"
                                            }
                                        }
                                    ]
                                },
                                {
                                    "code": "gradeLevel",
                                    "name": {
                                        "en": "Class",
                                        "ar": "الصف",
                                        "fr": "Classe",
                                        "pt": "Classe"
                                    },
                                    "label": {
                                        "en": "Class",
                                        "ar": "الصف",
                                        "fr": "Classe",
                                        "pt": "Classe"
                                    },
                                    "placeholder": {
                                        "en": "Select Class",
                                        "ar": "اختر الصف",
                                        "fr": "Sélectionner la Classe",
                                        "pt": "Selecionar a Classe"
                                    },
                                    "description": {
                                        "en": "Class of the Question Set",
                                        "ar": "صف مجموعة الأسئلة",
                                        "fr": "Classe de l'\''ensemble de questions",
                                        "pt": "Classe do conjunto de perguntas"
                                    },
                                    "default": "",
                                    "dataType": "list",
                                    "inputType": "select",
                                    "editable": true,
                                    "required": true,
                                    "visible": true,
                                    "depends": [
                                        "board",
                                        "medium"
                                    ],
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1 required"
                                    },
                                    "validations": [
                                        {
                                            "type": "required",
                                            "message": {
                                                "en": "Class is required",
                                                "ar": "الصف مطلوب",
                                                "fr": "La classe est requise",
                                                "pt": "A classe é obrigatória"
                                            }
                                        }
                                    ]
                                },
                                {
                                    "code": "subject",
                                    "name": {
                                        "en": "Subject",
                                        "ar": "المادة",
                                        "fr": "Matière",
                                        "pt": "Disciplina"
                                    },
                                    "label": {
                                        "en": "Subject",
                                        "ar": "المادة",
                                        "fr": "Matière",
                                        "pt": "Disciplina"
                                    },
                                    "placeholder": {
                                        "en": "Select Subject",
                                        "ar": "اختر المادة",
                                        "fr": "Sélectionner la Matière",
                                        "pt": "Selecionar a Disciplina"
                                    },
                                    "description": {
                                        "en": "Subject of the Question Set",
                                        "ar": "مادة مجموعة الأسئلة",
                                        "fr": "Matière de l'\''ensemble de questions",
                                        "pt": "Disciplina do conjunto de perguntas"
                                    },
                                    "default": "",
                                    "dataType": "list",
                                    "inputType": "select",
                                    "editable": true,
                                    "required": true,
                                    "visible": true,
                                    "depends": [
                                        "board",
                                        "medium",
                                        "gradeLevel"
                                    ],
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1 required"
                                    },
                                    "validations": [
                                        {
                                            "type": "required",
                                            "message": {
                                                "en": "Subject is required",
                                                "ar": "المادة مطلوبة",
                                                "fr": "La matière est requise",
                                                "pt": "A disciplina é obrigatória"
                                            }
                                        }
                                    ]
                                },
                                {
                                    "code": "audience",
                                    "name": {
                                        "en": "Audience",
                                        "ar": "الجمهور",
                                        "fr": "Public",
                                        "pt": "Público"
                                    },
                                    "label": {
                                        "en": "Audience",
                                        "ar": "الجمهور",
                                        "fr": "Public",
                                        "pt": "Público"
                                    },
                                    "placeholder": {
                                        "en": "Select Audience",
                                        "ar": "اختر الجمهور",
                                        "fr": "Sélectionner le Public",
                                        "pt": "Selecionar o Público"
                                    },
                                    "description": {
                                        "en": "Audience of the Question Set",
                                        "ar": "جمهور مجموعة الأسئلة",
                                        "fr": "Public de l'\''ensemble de questions",
                                        "pt": "Público do conjunto de perguntas"
                                    },
                                    "dataType": "list",
                                    "inputType": "select",
                                    "editable": true,
                                    "required": true,
                                    "visible": true,
                                    "range": [
                                        "Student",
                                        "Teacher",
                                        "Administrator"
                                    ],
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1 required"
                                    },
                                    "validations": [
                                        {
                                            "type": "required",
                                            "message": {
                                                "en": "Audience is required",
                                                "ar": "الجمهور مطلوب",
                                                "fr": "Le public est requis",
                                                "pt": "O público é obrigatório"
                                            }
                                        }
                                    ]
                                }
                            ]
                        },
                        {
                            "name": {
                                "en": "Question set behaviour",
                                "ar": "سلوك مجموعة الأسئلة",
                                "fr": "Comportement de l'\''ensemble de questions",
                                "pt": "Comportamento do conjunto de perguntas"
                            },
                            "fields": [
                                {
                                    "code": "maxTime",
                                    "name": {
                                        "en": "MaxTimer",
                                        "ar": "الحد الأقصى للمؤقت",
                                        "fr": "Minuteur maximum",
                                        "pt": "Temporizador máximo"
                                    },
                                    "label": {
                                        "en": "Set Maximum Time",
                                        "ar": "تعيين الوقت الأقصى",
                                        "fr": "Définir le temps maximum",
                                        "pt": "Definir tempo máximo"
                                    },
                                    "placeholder": {
                                        "en": "HH:mm:ss",
                                        "ar": "ساعة:دقيقة:ثانية",
                                        "fr": "HH:mm:ss",
                                        "pt": "HH:mm:ss"
                                    },
                                    "description": {
                                        "en": "This is the maximum time allowed for the users to complete the assessment",
                                        "ar": "هذا هو الوقت الأقصى المسموح للمستخدمين لإكمال التقييم",
                                        "fr": "C'\''est le temps maximum autorisé pour que les utilisateurs complètent l'\''évaluation",
                                        "pt": "Este é o tempo máximo permitido para os utilizadores completarem a avaliação"
                                    },
                                    "default": "3600",
                                    "dataType": "text",
                                    "inputType": "timer",
                                    "editable": true,
                                    "required": true,
                                    "visible": true,
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1"
                                    },
                                    "validations": [
                                        {
                                            "type": "time",
                                            "message": {
                                                "en": "Please enter in hh:mm:ss",
                                                "ar": "يرجى الإدخال بتنسيق ساعة:دقيقة:ثانية",
                                                "fr": "Veuillez saisir au format hh:mm:ss",
                                                "pt": "Por favor, introduza no formato hh:mm:ss"
                                            },
                                            "value": "HH:mm:ss"
                                        },
                                        {
                                            "type": "max",
                                            "value": "05:59:59",
                                            "message": {
                                                "en": "max time should be less than 05:59:59",
                                                "ar": "يجب أن يكون الوقت الأقصى أقل من 05:59:59",
                                                "fr": "Le temps maximum doit être inférieur à 05:59:59",
                                                "pt": "O tempo máximo deve ser inferior a 05:59:59"
                                            }
                                        }
                                    ]
                                },
                                {
                                    "code": "showTimer",
                                    "name": {
                                        "en": "show Timer",
                                        "ar": "إظهار المؤقت",
                                        "fr": "Afficher le minuteur",
                                        "pt": "Mostrar temporizador"
                                    },
                                    "label": {
                                        "en": "show Timer",
                                        "ar": "إظهار المؤقت",
                                        "fr": "Afficher le minuteur",
                                        "pt": "Mostrar temporizador"
                                    },
                                    "placeholder": {
                                        "en": "show Timer",
                                        "ar": "إظهار المؤقت",
                                        "fr": "Afficher le minuteur",
                                        "pt": "Mostrar temporizador"
                                    },
                                    "description": {
                                        "en": "show Timer",
                                        "ar": "إظهار المؤقت",
                                        "fr": "Afficher le minuteur",
                                        "pt": "Mostrar temporizador"
                                    },
                                    "default": false,
                                    "dataType": "boolean",
                                    "inputType": "checkbox",
                                    "editable": true,
                                    "required": false,
                                    "visible": true,
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1"
                                    }
                                },
                                {
                                    "code": "requiresSubmit",
                                    "name": {
                                        "en": "Submit Confirmation",
                                        "ar": "تأكيد الإرسال",
                                        "fr": "Confirmation de soumission",
                                        "pt": "Confirmação de envio"
                                    },
                                    "label": {
                                        "en": "Submit Confirmation Page",
                                        "ar": "صفحة تأكيد الإرسال",
                                        "fr": "Page de confirmation de soumission",
                                        "pt": "Página de confirmação de envio"
                                    },
                                    "placeholder": {
                                        "en": "Select Submit Confirmation",
                                        "ar": "اختر تأكيد الإرسال",
                                        "fr": "Sélectionner la confirmation de soumission",
                                        "pt": "Selecionar confirmação de envio"
                                    },
                                    "description": {
                                        "en": "Allows users to review and submit the assessment",
                                        "ar": "يسمح للمستخدمين بمراجعة التقييم وتقديمه",
                                        "fr": "Permet aux utilisateurs de réviser et de soumettre l'\''évaluation",
                                        "pt": "Permite que os utilizadores revisem e submetam a avaliação"
                                    },
                                    "dataType": "text",
                                    "inputType": "select",
                                    "output": "identifier",
                                    "range": [
                                        {
                                            "identifier": "Yes",
                                            "label": "Enable"
                                        },
                                        {
                                            "identifier": "No",
                                            "label": "Disable"
                                        }
                                    ],
                                    "editable": true,
                                    "required": false,
                                    "visible": true,
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1"
                                    }
                                },
                                {
                                    "code": "maxAttempts",
                                    "name": {
                                        "en": "Max Attempts",
                                        "ar": "الحد الأقصى للمحاولات",
                                        "fr": "Tentatives maximales",
                                        "pt": "Tentativas máximas"
                                    },
                                    "label": {
                                        "en": "Max Attempts",
                                        "ar": "الحد الأقصى للمحاولات",
                                        "fr": "Tentatives maximales",
                                        "pt": "Tentativas máximas"
                                    },
                                    "placeholder": {
                                        "en": "Max Attempts",
                                        "ar": "الحد الأقصى للمحاولات",
                                        "fr": "Tentatives maximales",
                                        "pt": "Tentativas máximas"
                                    },
                                    "description": {
                                        "en": "Max Attempts",
                                        "ar": "الحد الأقصى للمحاولات",
                                        "fr": "Tentatives maximales",
                                        "pt": "Tentativas máximas"
                                    },
                                    "dataType": "number",
                                    "inputType": "select",
                                    "editable": true,
                                    "required": false,
                                    "visible": true,
                                    "range": [
                                        1,
                                        2,
                                        3,
                                        4,
                                        5,
                                        6,
                                        7,
                                        8,
                                        9,
                                        10,
                                        11,
                                        12,
                                        13,
                                        14,
                                        15,
                                        16,
                                        17,
                                        18,
                                        19,
                                        20,
                                        21,
                                        22,
                                        23,
                                        24,
                                        25
                                    ],
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1"
                                    }
                                },
                                {
                                    "code": "summaryType",
                                    "name": {
                                        "en": "summaryType",
                                        "ar": "نوع الملخص",
                                        "fr": "Type de résumé",
                                        "pt": "Tipo de resumo"
                                    },
                                    "label": {
                                        "en": "Summary Type",
                                        "ar": "نوع الملخص",
                                        "fr": "Type de résumé",
                                        "pt": "Tipo de resumo"
                                    },
                                    "placeholder": {
                                        "en": "Select Summary Type",
                                        "ar": "اختر نوع الملخص",
                                        "fr": "Sélectionner le type de résumé",
                                        "pt": "Selecionar tipo de resumo"
                                    },
                                    "description": {
                                        "en": "summaryType",
                                        "ar": "نوع الملخص",
                                        "fr": "Type de résumé",
                                        "pt": "Tipo de resumo"
                                    },
                                    "dataType": "text",
                                    "inputType": "select",
                                    "editable": true,
                                    "required": false,
                                    "visible": true,
                                    "range": [
                                        "Complete",
                                        "Score",
                                        "Duration",
                                        "Score and Duration"
                                    ],
                                    "renderingHints": {
                                        "class": "sb-g-col-lg-1"
                                    }
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
                            "description": {
                                "en": "Type",
                                "ar": "النوع",
                                "fr": "Type",
                                "pt": "Tipo"
                            },
                            "editable": true,
                            "default": [],
                            "renderingHints": {
                                "class": "sb-g-col-lg-1"
                            },
                            "inputType": "nestedselect",
                            "label": {
                                "en": "Question Type(s)",
                                "ar": "نوع(أنواع) السؤال",
                                "fr": "Type(s) de question",
                                "pt": "Tipo(s) de pergunta"
                            },
                            "name": {
                                "en": "Type",
                                "ar": "النوع",
                                "fr": "Type",
                                "pt": "Tipo"
                            },
                            "placeholder": {
                                "en": "Select QuestionType",
                                "ar": "اختر نوع السؤال",
                                "fr": "Sélectionner le type de question",
                                "pt": "Selecionar tipo de pergunta"
                            },
                            "required": false,
                            "visible": true
                        },
                        {
                            "code": "board",
                            "visible": true,
                            "depends": [],
                            "editable": true,
                            "dataType": "list",
                            "description": {
                                "en": "Board",
                                "ar": "المجلس",
                                "fr": "Conseil",
                                "pt": "Conselho"
                            },
                            "label": {
                                "en": "Board",
                                "ar": "المجلس",
                                "fr": "Conseil",
                                "pt": "Conselho"
                            },
                            "required": false,
                            "name": {
                                "en": "Board",
                                "ar": "المجلس",
                                "fr": "Conseil",
                                "pt": "Conselho"
                            },
                            "inputType": "select",
                            "placeholder": {
                                "en": "Select Board",
                                "ar": "اختر المجلس",
                                "fr": "Sélectionner le Conseil",
                                "pt": "Selecionar o Conselho"
                            },
                            "output": "name",
                            "renderingHints": {
                                "class": "sb-g-col-lg-1"
                            }
                        },
                        {
                            "code": "medium",
                            "visible": true,
                            "editable": true,
                            "dataType": "list",
                            "description": {
                                "en": "Medium of Question",
                                "ar": "وسيط السؤال",
                                "fr": "Médium de la question",
                                "pt": "Médio da pergunta"
                            },
                            "label": {
                                "en": "Medium(s)",
                                "ar": "الوسيط(وسائط)",
                                "fr": "Médium(s)",
                                "pt": "Médio(s)"
                            },
                            "required": false,
                            "name": {
                                "en": "Medium",
                                "ar": "الوسيط",
                                "fr": "Médium",
                                "pt": "Médio"
                            },
                            "inputType": "nestedselect",
                            "placeholder": {
                                "en": "Select Medium",
                                "ar": "اختر الوسيط",
                                "fr": "Sélectionner le Médium",
                                "pt": "Selecionar o Médio"
                            },
                            "output": "name",
                            "depends": [
                                "board"
                            ],
                            "renderingHints": {
                                "class": "sb-g-col-lg-1"
                            }
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
                            "description": {
                                "en": "Class",
                                "ar": "الصف",
                                "fr": "Classe",
                                "pt": "Classe"
                            },
                            "label": {
                                "en": "Class(es)",
                                "ar": "الصف(صفوف)",
                                "fr": "Classe(s)",
                                "pt": "Classe(s)"
                            },
                            "required": false,
                            "name": {
                                "en": "Class",
                                "ar": "الصف",
                                "fr": "Classe",
                                "pt": "Classe"
                            },
                            "inputType": "nestedselect",
                            "placeholder": {
                                "en": "Select Class",
                                "ar": "اختر الصف",
                                "fr": "Sélectionner la Classe",
                                "pt": "Selecionar a Classe"
                            },
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
                            "description": {
                                "en": "Subject of the Question",
                                "ar": "مادة السؤال",
                                "fr": "Matière de la question",
                                "pt": "Disciplina da pergunta"
                            },
                            "label": {
                                "en": "Subject(s)",
                                "ar": "المادة(مواد)",
                                "fr": "Matière(s)",
                                "pt": "Disciplina(s)"
                            },
                            "required": false,
                            "name": {
                                "en": "Subject",
                                "ar": "المادة",
                                "fr": "Matière",
                                "pt": "Disciplina"
                            },
                            "inputType": "nestedselect",
                            "placeholder": {
                                "en": "Select Subject",
                                "ar": "اختر المادة",
                                "fr": "Sélectionner la Matière",
                                "pt": "Selecionar a Disciplina"
                            },
                            "output": "name"
                        }
                    ]
                },
                "unitMetadata": {
                    "templateName": "",
                    "required": [],
                    "properties": [
                        {
                            "code": "name",
                            "dataType": "text",
                            "description": {
                                "en": "Name of the content",
                                "ar": "اسم المحتوى",
                                "fr": "Nom du contenu",
                                "pt": "Nome do conteúdo"
                            },
                            "editable": true,
                            "inputType": "text",
                            "label": {
                                "en": "Title",
                                "ar": "عنوان",
                                "fr": "Titre",
                                "pt": "Título"
                            },
                            "name": {
                                "en": "Title",
                                "ar": "عنوان",
                                "fr": "Titre",
                                "pt": "Título"
                            },
                            "placeholder": {
                                "en": "Title",
                                "ar": "عنوان",
                                "fr": "Titre",
                                "pt": "Título"
                            },
                            "renderingHints": {
                                "class": "sb-g-col-lg-1 required"
                            },
                            "required": true,
                            "visible": true,
                            "validations": [
                                {
                                    "type": "max",
                                    "value": "120",
                                    "message": {
                                        "en": "Input is Exceeded",
                                        "ar": "تم تجاوز الحد",
                                        "fr": "La saisie est dépassée",
                                        "pt": "A entrada foi excedida"
                                    }
                                },
                                {
                                    "type": "required",
                                    "message": {
                                        "en": "Title is required",
                                        "ar": "العنوان مطلوب",
                                        "fr": "Le titre est requis",
                                        "pt": "O título é obrigatório"
                                    }
                                }
                            ]
                        },
                        {
                            "code": "description",
                            "dataType": "text",
                            "description": {
                                "en": "Description of the content",
                                "ar": "وصف المحتوى",
                                "fr": "Description du contenu",
                                "pt": "Descrição do conteúdo"
                            },
                            "editable": true,
                            "inputType": "textarea",
                            "label": {
                                "en": "Description",
                                "ar": "الوصف",
                                "fr": "Description",
                                "pt": "Descrição"
                            },
                            "name": {
                                "en": "Description",
                                "ar": "الوصف",
                                "fr": "Description",
                                "pt": "Descrição"
                            },
                            "placeholder": {
                                "en": "Description",
                                "ar": "الوصف",
                                "fr": "Description",
                                "pt": "Descrição"
                            },
                            "renderingHints": {
                                "class": "sb-g-col-lg-1 required"
                            },
                            "required": true,
                            "visible": true,
                            "validations": [
                                {
                                    "type": "max",
                                    "value": "500",
                                    "message": {
                                        "en": "Input is Exceeded",
                                        "ar": "تم تجاوز الحد",
                                        "fr": "La saisie est dépassée",
                                        "pt": "A entrada foi excedida"
                                    }
                                }
                            ]
                        },
                        {
                            "code": "instructions",
                            "name": {
                                "en": "Instructions",
                                "ar": "التعليمات",
                                "fr": "Instructions",
                                "pt": "Instruções"
                            },
                            "label": {
                                "en": "Instructions",
                                "ar": "التعليمات",
                                "fr": "Instructions",
                                "pt": "Instruções"
                            },
                            "placeholder": {
                                "en": "Enter Instructions",
                                "ar": "أدخل التعليمات",
                                "fr": "Entrer les instructions",
                                "pt": "Inserir instruções"
                            },
                            "description": {
                                "en": "Instructions for the section",
                                "ar": "التعليمات للقسم",
                                "fr": "Instructions pour la section",
                                "pt": "Instruções para a secção"
                            },
                            "dataType": "text",
                            "inputType": "richtext",
                            "editable": true,
                            "required": false,
                            "visible": true,
                            "renderingHints": {
                                "class": "sb-g-col-lg-2 required"
                            },
                            "validations": [
                                {
                                    "type": "maxLength",
                                    "value": "500",
                                    "message": {
                                        "en": "Input is Exceeded",
                                        "ar": "تم تجاوز الحد",
                                        "fr": "La saisie est dépassée",
                                        "pt": "A entrada foi excedida"
                                    }
                                }
                            ]
                        },
                        {
                            "code": "maxQuestions",
                            "name": {
                                "en": "Show Questions",
                                "ar": "إظهار الأسئلة",
                                "fr": "Afficher les questions",
                                "pt": "Mostrar perguntas"
                            },
                            "label": {
                                "en": "Count of questions to be displayed in this section",
                                "ar": "عدد الأسئلة التي سيتم عرضها في هذا القسم",
                                "fr": "Nombre de questions à afficher dans cette section",
                                "pt": "Número de perguntas a exibir nesta secção"
                            },
                            "placeholder": {
                                "en": "Input count of questions to be displayed",
                                "ar": "أدخل عدد الأسئلة التي سيتم عرضها",
                                "fr": "Entrer le nombre de questions à afficher",
                                "pt": "Inserir número de perguntas a exibir"
                            },
                            "description": {
                                "en": "By default all questions are shown unless specific count is entered.",
                                "ar": "بشكل افتراضي تُعرض جميع الأسئلة ما لم يُدخل عدد محدد.",
                                "fr": "Par défaut, toutes les questions sont affichées sauf si un nombre spécifique est saisi.",
                                "pt": "Por defeito, todas as perguntas são mostradas a menos que seja inserido um número específico."
                            },
                            "default": "",
                            "dataType": "number",
                            "inputType": "select",
                            "editable": true,
                            "required": false,
                            "visible": true,
                            "renderingHints": {
                                "class": "sb-g-col-lg-1"
                            }
                        },
                        {
                            "code": "shuffle",
                            "name": {
                                "en": "Shuffle Questions",
                                "ar": "ترتيب عشوائي للأسئلة",
                                "fr": "Mélanger les questions",
                                "pt": "Embaralhar perguntas"
                            },
                            "label": {
                                "en": "Shuffle Questions",
                                "ar": "ترتيب عشوائي للأسئلة",
                                "fr": "Mélanger les questions",
                                "pt": "Embaralhar perguntas"
                            },
                            "placeholder": {
                                "en": "Shuffle Questions",
                                "ar": "ترتيب عشوائي للأسئلة",
                                "fr": "Mélanger les questions",
                                "pt": "Embaralhar perguntas"
                            },
                            "description": {
                                "en": "If shuffle questions is selected, users are presented with questions in a random order whenever they attempt the assessment",
                                "ar": "إذا تم اختيار الترتيب العشوائي، يُعرض على المستخدمين الأسئلة بترتيب عشوائي في كل مرة يحاولون فيها التقييم",
                                "fr": "Si le mélange des questions est sélectionné, les utilisateurs reçoivent les questions dans un ordre aléatoire à chaque tentative d'\''évaluation",
                                "pt": "Se embaralhar perguntas estiver selecionado, os utilizadores recebem as perguntas numa ordem aleatória sempre que tentam a avaliação"
                            },
                            "default": "false",
                            "dataType": "boolean",
                            "inputType": "checkbox",
                            "editable": true,
                            "required": false,
                            "visible": true,
                            "renderingHints": {
                                "class": "sb-g-col-lg-1"
                            }
                        },
                        {
                            "code": "showFeedback",
                            "name": {
                                "en": "Show Feedback",
                                "ar": "إظهار التغذية الراجعة",
                                "fr": "Afficher les commentaires",
                                "pt": "Mostrar feedback"
                            },
                            "label": {
                                "en": "Show Question Feedback",
                                "ar": "إظهار تغذية راجعة للسؤال",
                                "fr": "Afficher les commentaires sur la question",
                                "pt": "Mostrar feedback da pergunta"
                            },
                            "placeholder": {
                                "en": "Select Option",
                                "ar": "اختر خيارًا",
                                "fr": "Sélectionner une option",
                                "pt": "Selecionar opção"
                            },
                            "description": {
                                "en": "If feedback is selected, users are informed whether they have correctly answered question or not",
                                "ar": "إذا تم اختيار التغذية الراجعة، يتم إعلام المستخدمين بما إذا كانوا قد أجابوا على السؤال بشكل صحيح أم لا",
                                "fr": "Si les commentaires sont sélectionnés, les utilisateurs sont informés s'\''ils ont correctement répondu à la question ou non",
                                "pt": "Se o feedback estiver selecionado, os utilizadores são informados se responderam corretamente à pergunta ou não"
                            },
                            "dataType": "boolean",
                            "inputType": "checkbox",
                            "editable": true,
                            "required": false,
                            "visible": true,
                            "renderingHints": {
                                "class": "sb-g-col-lg-1"
                            }
                        },
                        {
                            "code": "showSolutions",
                            "name": {
                                "en": "Show Solution",
                                "ar": "إظهار الحل",
                                "fr": "Afficher la solution",
                                "pt": "Mostrar solução"
                            },
                            "label": {
                                "en": "Show Solution",
                                "ar": "إظهار الحل",
                                "fr": "Afficher la solution",
                                "pt": "Mostrar solução"
                            },
                            "placeholder": {
                                "en": "Select Option",
                                "ar": "اختر خيارًا",
                                "fr": "Sélectionner une option",
                                "pt": "Selecionar opção"
                            },
                            "description": {
                                "en": "If show solution is selected then solutions for each question will be shown to the user",
                                "ar": "إذا تم اختيار إظهار الحل، ستُعرض حلول كل سؤال للمستخدم",
                                "fr": "Si afficher la solution est sélectionné, les solutions pour chaque question seront affichées à l'\''utilisateur",
                                "pt": "Se mostrar solução estiver selecionado, as soluções para cada pergunta serão mostradas ao utilizador"
                            },
                            "dataType": "boolean",
                            "inputType": "checkbox",
                            "editable": true,
                            "required": false,
                            "visible": true,
                            "renderingHints": {
                                "class": "sb-g-col-lg-1"
                            }
                        }
                    ]
                }
            }
        }
    }
}'
