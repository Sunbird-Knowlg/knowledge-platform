package utils

object ApiId {

	final val APPLICATION_HEALTH = "api.content-service.health"

	//Content APIs
	val CREATE_CONTENT = "api.content.create"
	val READ_CONTENT = "api.content.read"
	val UPDATE_CONTENT = "api.content.update"
	val UPLOAD_CONTENT = "api.content.upload"
	val COPY_CONTENT = "api.content.copy"
	val UPLOAD_PRE_SIGNED_CONTENT = "api.content.upload.url"

	// Collection APIs
	val ADD_HIERARCHY = "api.content.hierarchy.add"
	val REMOVE_HIERARCHY = "api.content.hierarchy.remove"
	val UPDATE_HIERARCHY = "api.content.hierarchy.update"
	val GET_HIERARCHY = "api.content.hierarchy.get"

	//License APIs
	val CREATE_LICENSE = "api.license.create"
	val READ_LICENSE = "api.license.read"
	val UPDATE_LICENSE = "api.license.update"
	val RETIRE_LICENSE = "api.license.retire"

	//Channel APIs
	val CREATE_CHANNEL = "api.channel.create"
	val READ_CHANNEL = "api.channel.read"
	val UPDATE_CHANNEL = "api.channel.update"
	val LIST_CHANNEL = "api.channel.list"
	val RETIRE_CHANNEL = "api.channel.retire"



}
