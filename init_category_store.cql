CREATE KEYSPACE IF NOT EXISTS dev_category_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};

CREATE TABLE IF NOT EXISTS dev_category_store.category_definition_data (identifier text, objectmetadata map<text, text>, forms map<text,text> ,PRIMARY KEY (identifier));

INSERT INTO dev_category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_content_all', {'config': '{}', 'schema': '{"properties":{"audience":{"type":"array","items":{"type":"string","enum":["Student","Teacher"]},"default":["Student"]},"mimeType":{"type":"string","enum":["application/vnd.ekstep.ecml-archive","application/vnd.ekstep.html-archive","application/vnd.ekstep.h5p-archive","application/pdf","video/mp4","video/webm"]}}}'});

INSERT INTO dev_category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:course_collection_all', {'config': '{}', 'schema': '{"properties":{"trackable":{"type":"object","properties":{"enabled":{"type":"string","enum":["Yes","No"],"default":"No"},"autoBatch":{"type":"string","enum":["Yes","No"],"default":"No"}},"default":{"enabled":"No","autoBatch":"No"},"additionalProperties":false},"additionalCategories":{"type":"array","items":{"type":"string","default":"Textbook"}},"userConsent":{"type":"string","enum":["Yes","No"],"default":"Yes"}}}'});

INSERT INTO dev_category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:course_content_all',{'config': '{}', 'schema': '{"properties":{"trackable":{"type":"object","properties":{"enabled":{"type":"string","enum":["Yes","No"],"default":"No"},"autoBatch":{"type":"string","enum":["Yes","No"],"default":"No"}},"default":{"enabled":"No","autoBatch":"No"},"additionalProperties":false},"additionalCategories":{"type":"array","items":{"type":"string","default":"Textbook"}},"userConsent":{"type":"string","enum":["Yes","No"],"default":"Yes"}}}'});

INSERT INTO dev_category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_collection_all', {'config': '{}', 'schema': '{"properties":{"audience":{"type":"array","items":{"type":"string","enum":["Student","Teacher"]},"default":["Student"]},"mimeType":{"type":"string","enum":["application/vnd.ekstep.ecml-archive","application/vnd.ekstep.html-archive","application/vnd.ekstep.h5p-archive","application/pdf","video/mp4","video/webm"]}}}'});

INSERT INTO dev_category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_content_in.ekstep', {'config': '{}', 'schema': '{"properties":{"audience":{"type":"array","items":{"type":"string","enum":["Student","Teacher"]},"default":["Student"]},"mimeType":{"type":"string","enum":["application/vnd.ekstep.ecml-archive","application/vnd.ekstep.html-archive","application/vnd.ekstep.h5p-archive","application/pdf","video/mp4","video/webm"]}}}'});

INSERT INTO dev_category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_collection_in.ekstep', {'config': '{}', 'schema': '{"properties":{"audience":{"type":"array","items":{"type":"string","enum":["Student","Teacher"]},"default":["Student"]},"mimeType":{"type":"string","enum":["application/vnd.ekstep.ecml-archive","application/vnd.ekstep.html-archive","application/vnd.ekstep.h5p-archive","application/pdf","video/mp4","video/webm"]}}}'});

INSERT INTO dev_category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:practice-question_question_all', {'config': '{}', 'schema': '{}'});

INSERT INTO dev_category_store.category_definition_data(identifier) values ('obj-cat:digital-textbook_collection_all');
INSERT INTO dev_category_store.category_definition_data(identifier) values ('obj-cat:textbook-unit_collection_all');
INSERT INTO dev_category_store.category_definition_data(identifier) values ('obj-cat:course-unit_collection_all');
INSERT INTO dev_category_store.category_definition_data(identifier) values ('obj-cat:content-playlist_collection_all');
INSERT INTO dev_category_store.category_definition_data(identifier) values ('obj-cat:asset_asset_all');
INSERT INTO dev_category_store.category_definition_data(identifier) values ('obj-cat:explanation-content_content_all');
