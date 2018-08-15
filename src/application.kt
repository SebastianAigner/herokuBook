package io.sebi

import io.ktor.application.*
import io.ktor.content.PartData
import io.ktor.content.readAllParts
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.html.*
import kotlinx.html.*
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

fun main(args: Array<String>): Unit = io.ktor.server.netty.DevelopmentEngine.main(args)

fun Application.module() {
	Database.connect(System.getenv("JDBC_DATABASE_URL"), driver = "org.postgresql.Driver")

	transaction {
		create(GuestbookEntries)
		if(GuestbookEntry.count() == 0) {
			GuestbookEntry.new {
				text = "Thank you for stopping by!"
				creation = DateTime.now()
			}
		}
	}

	routing {
		get("/") {
			val entries = transaction { GuestbookEntry.all().toList() }
			call.respondHtml {
				body {
					form("/", FormEncType.multipartFormData, FormMethod.post) {
						acceptCharset = "utf-8"
						p {
							label { +"Add a new entry!" }
							textInput { name = "entry" }
						}
						input {
							type = InputType.submit
						}
					}
					for(i in entries) {
						p {
							+"At ${i.creation}: ${i.text}"
						}
					}
				}
			}
		}

		post("/") {
			val multipart = call.receiveMultipart()
			val formItems = multipart.readAllParts().filterIsInstance<PartData.FormItem>()
			val myMap = formItems.map { it.name to it.value }.toMap()
			myMap["entry"]?.let {  ent ->
				transaction {
					GuestbookEntries.insert {
						it[text] = ent
						it[creation] = DateTime.now()
					}
				}
			}
			call.respondRedirect("/")
		}
	}
}

object GuestbookEntries: IntIdTable() {
	val text = varchar("text", 255)
	val creation = date("creation")
}

class GuestbookEntry(id: EntityID<Int>): IntEntity(id) {
	companion object: IntEntityClass<GuestbookEntry>(GuestbookEntries)
	var text by GuestbookEntries.text
	var creation by GuestbookEntries.creation
}