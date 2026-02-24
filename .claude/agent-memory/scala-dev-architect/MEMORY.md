# Persistent Agent Memory - Scala Dev Architect

## Key Patterns

### cloud-storage-sdk 2.0.0 Migration (platform-modules/mimetype-manager)
- `StorageService.scala`, `IProcessor.scala`, `MimeTypeManagerFactory.scala` are already fixed as of Feb 2026
- SDK uses Java primitives: `false/true` (not `Option[Boolean]`), `null.asInstanceOf[Integer]` (not `None`/`Option.empty`)
- `uploadFolder` returns `CompletableFuture[java.util.List[String]]` — convert via `.asScala.map(_.asScala.toList)`
- `blob.getContentLength().toDouble` (Java getter, not Scala field)
- `getSignedURL(container, key, ttl.map(i => i: Integer).orNull, permission.orNull)`
- `MimeTypeManagerFactory.getManager` takes `implicit ss: StorageService` and constructs managers inside the method

### Test Patterns
- Network-dependent tests (external URLs like `sunbirddev.blob.core.windows.net`) must use `ignore should` not `it should`
- `EcmlMimeTypeMgrImplTest`: "upload ECML with json zip file URL" test is ignored because it hits an external Azure blob URL

## Module Build Commands
```bash
# mimetype-manager (with tests)
mvn clean install -pl platform-modules/mimetype-manager

# content-api submodules
mvn clean install -DskipTests -pl content-api/content-actors,content-api/content-service,content-api/hierarchy-manager,content-api/content-controllers
```

## Key File Paths
- `platform-modules/mimetype-manager/src/main/scala/org/sunbird/cloudstore/StorageService.scala`
- `platform-modules/mimetype-manager/src/main/scala/org/sunbird/mimetype/ecml/processor/IProcessor.scala`
- `platform-modules/mimetype-manager/src/main/scala/org/sunbird/mimetype/factory/MimeTypeManagerFactory.scala`
- `platform-modules/mimetype-manager/src/main/scala/org/sunbird/mimetype/mgr/BaseMimeTypeManager.scala`

## Common Build Issues
- Netty version duplication warnings in cassandra-connector and content-service POMs — warnings only, not errors
- `content-api` parent POM builds trivially; specify submodules explicitly with `-pl`
