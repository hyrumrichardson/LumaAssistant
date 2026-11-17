package io.github.hyrumrichardson.luma.nlu

import io.github.hyrumrichardson.luma.phone.Contact
import io.github.hyrumrichardson.luma.phone.ContactMatch
import io.github.hyrumrichardson.luma.phone.ContactMatcher
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskParserTest {
    private fun parse(text: String) = CommandParser.parse(text)

    @Test fun addTaskTo() = assertEquals(Command.AddTask("Buy milk"), parse("Add a task to buy milk"))

    @Test fun addTaskKeepsCase() = assertEquals(Command.AddTask("Call Bob about the 2% milk"), parse("add a task to call Bob about the 2% milk"))

    @Test fun newTask() = assertEquals(Command.AddTask("Water the plants"), parse("new task water the plants"))

    @Test fun newTaskColon() = assertEquals(Command.AddTask("Water the plants"), parse("New task: water the plants."))

    @Test fun createTaskCalled() = assertEquals(Command.AddTask("Pay rent"), parse("Hey Luma, create a task called pay rent please"))

    @Test fun addTaskToMyList() = assertEquals(Command.AddTask("Pay rent"), parse("add a task to my list called pay rent"))

    @Test fun addToMyTasks() = assertEquals(Command.AddTask("Buy milk"), parse("add buy milk to my tasks"))

    @Test fun putOnTaskList() = assertEquals(Command.AddTask("Call the dentist"), parse("put call the dentist on my task list"))

    @Test fun addToSuperProductivity() = assertEquals(Command.AddTask("Buy milk"), parse("add buy milk to super productivity"))

    @Test fun addAsTask() = assertEquals(Command.AddTask("Fix the fence"), parse("add fix the fence as a task"))

    @Test fun bareTask() = assertEquals(Command.AddTask("Buy milk"), parse("task buy milk"))

    @Test fun addTaskNoTitle() = assertEquals(Command.AddTask(null), parse("add a task"))

    @Test fun createNewTaskNoTitle() = assertEquals(Command.AddTask(null), parse("create a new task"))

    @Test fun addTaskWithChangeInTitle() = assertEquals(Command.AddTask("Change the oil"), parse("add a task to change the oil"))

    @Test fun completeTask() = assertEquals(Command.CompleteTask("Buy milk"), parse("complete task buy milk"))

    @Test fun markTaskDone() = assertEquals(Command.CompleteTask("Buy milk"), parse("mark task buy milk as done"))

    @Test fun markTheXTaskDone() = assertEquals(Command.CompleteTask("Buy milk"), parse("mark the buy milk task as done"))

    @Test fun completeTheXTask() = assertEquals(Command.CompleteTask("Laundry"), parse("complete the laundry task"))

    @Test fun finishedTheTask() = assertEquals(Command.CompleteTask("Call Bob"), parse("I finished the task call Bob"))

    @Test fun doneWithTask() = assertEquals(Command.CompleteTask("Homework"), parse("I'm done with the homework task"))

    @Test fun checkOffList() = assertEquals(Command.CompleteTask("Buy milk"), parse("check off buy milk on my task list"))

    @Test fun crossOffTodoList() = assertEquals(Command.CompleteTask("Buy milk"), parse("cross buy milk off my to do list"))

    @Test fun taskIsDone() = assertEquals(Command.CompleteTask("Buy milk"), parse("the buy milk task is done"))

    @Test fun showMyTasks() = assertEquals(Command.OpenTasks(TaskView.TODAY), parse("show my tasks"))

    @Test fun whatAreMyTasks() = assertEquals(Command.OpenTasks(TaskView.TODAY), parse("what are my tasks for today"))

    @Test fun checkMyTaskList() = assertEquals(Command.OpenTasks(TaskView.TODAY), parse("check my task list"))

    @Test fun taskInbox() = assertEquals(Command.OpenTasks(TaskView.INBOX), parse("open my task inbox"))

    @Test fun markTaskAsDoneWithoutTitle() = assertEquals(Command.OpenTasks(TaskView.TODAY), parse("mark task as done"))

    @Test fun deleteIsUnsupported() =
        assertEquals(Command.OpenTasks(TaskView.TODAY, unsupported = "delete"), parse("delete the buy milk task"))

    @Test fun renameIsUnsupported() =
        assertEquals(Command.OpenTasks(TaskView.TODAY, unsupported = "rename"), parse("rename my task to buy oat milk"))

    @Test fun anythingElseAboutTasksOpensTheApp() =
        assertEquals(Command.OpenTasks(TaskView.TODAY), parse("I need help with my tasks"))

    @Test fun taskLightIsADevice() =
        assertEquals(Command.ControlDevice("task light", true, Timing.Now), parse("turn on the task light"))

    @Test fun taskLightLooseIsADevice() =
        assertEquals(Command.ControlDevice("task light", false, Timing.Now), parse("task light off"))

    @Test fun timerIsStillATimer() =
        assertEquals(Command.SetTimer(10.minutes, null), parse("set a timer for 10 minutes"))
}

class TextParserTest {
    private fun parse(text: String) = CommandParser.parse(text)

    @Test fun textContact() = assertEquals(Command.SendText("Mom", null), parse("Text Mom"))

    @Test fun textContactFullName() = assertEquals(Command.SendText("John Smith", null), parse("text John Smith"))

    @Test fun textSaying() =
        assertEquals(Command.SendText("Mom", "I'm on my way"), parse("Text Mom saying I'm on my way"))

    @Test fun textKeepsPunctuation() =
        assertEquals(Command.SendText("Mom", "Are you home?"), parse("text Mom saying Are you home?"))

    @Test fun messageThat() =
        assertEquals(Command.SendText("John", "dinner is ready"), parse("message John that dinner is ready"))

    @Test fun sendATextTo() = assertEquals(Command.SendText("Dad", null), parse("send a text to Dad"))

    @Test fun sendATextMessageTo() =
        assertEquals(Command.SendText("Dad", "call me"), parse("Hey Luma, send a text message to Dad saying call me"))

    @Test fun sendContactAText() =
        assertEquals(Command.SendText("Sarah", "running late"), parse("send Sarah a text saying running late"))

    @Test fun sendContactAMessage() = assertEquals(Command.SendText("Sarah", null), parse("send Sarah a message"))

    @Test fun textComma() = assertEquals(Command.SendText("Mom", "I'm late"), parse("Text Mom, I'm late"))

    @Test fun nameRunsIntoMessage() =
        // Split later, against the contact list.
        assertEquals(Command.SendText("mom running late", null), parse("text mom running late"))

    @Test fun textToSpeechIsASearch() =
        assertEquals(Command.WebSearch("text to speech settings"), parse("text to speech settings"))

    @Test fun textingAboutATaskIsAText() =
        assertEquals(Command.SendText("Mom", "I finished the task"), parse("text Mom saying I finished the task"))
}

class AnswersTest {
    private fun classify(vararg text: String) = Answers.classify(text.toList())

    @Test fun yes() {
        listOf("Yes", "yeah", "yep", "Sure", "send it", "OK", "okay send it", "go ahead", "yes please", "Yes.").forEach {
            assertEquals(it, Answer.YES, classify(it))
        }
    }

    @Test fun no() {
        listOf("No", "nope", "don't send it", "cancel", "never mind", "nevermind", "forget it", "no thanks").forEach {
            assertEquals(it, Answer.NO, classify(it))
        }
    }

    @Test fun change() {
        listOf("change it", "no, change it", "edit", "try again", "start over", "let me redo it").forEach {
            assertEquals(it, Answer.CHANGE, classify(it))
        }
    }

    @Test fun unclear() = assertNull(classify("what time is it"))

    @Test fun usesLaterAlternatives() = assertEquals(Answer.YES, classify("guess", "yes"))

    @Test fun nothingIsNotNo() = assertNull(classify("nothing much"))

    @Test fun cancel() {
        assertTrue(Answers.isCancel("Cancel"))
        assertTrue(Answers.isCancel("never mind."))
        assertFalse(Answers.isCancel("cancel the dinner reservation"))
        assertFalse(Answers.isCancel("no"))
    }
}

class ContactMatcherTest {
    private val mom = Contact(1, "Mom", listOf("1"))
    private val john = Contact(2, "John Smith", listOf("2"))
    private val johnny = Contact(3, "John Doe", listOf("3"))
    private val jose = Contact(4, "José Álvarez", listOf("4"))
    private val sarah = Contact(5, "Sarah O'Brien", listOf("5"))
    private val contacts = listOf(mom, john, johnny, jose, sarah)

    private fun match(spoken: String, allowLeftover: Boolean = false) = ContactMatcher.match(spoken, contacts, allowLeftover)

    @Test fun exact() = assertEquals(ContactMatch.Found(mom, null), match("mom"))

    @Test fun my() = assertEquals(ContactMatch.Found(mom, null), match("my mom"))

    @Test fun fullName() = assertEquals(ContactMatch.Found(john, null), match("John Smith"))

    @Test fun firstNameAmbiguous() = assertEquals(ContactMatch.Ambiguous(listOf(john, johnny), null), match("john"))

    @Test fun lastName() = assertEquals(ContactMatch.Found(john, null), match("smith"))

    @Test fun accents() = assertEquals(ContactMatch.Found(jose, null), match("jose alvarez"))

    @Test fun apostrophe() = assertEquals(ContactMatch.Found(sarah, null), match("sarah obrien"))

    @Test fun prefixes() = assertEquals(ContactMatch.Found(john, null), match("john s"))

    @Test fun notFound() = assertEquals(ContactMatch.NotFound, match("grandma"))

    @Test fun leftover() = assertEquals(ContactMatch.Found(mom, "running late"), match("mom running late", allowLeftover = true))

    @Test fun leftoverKeepsCase() =
        assertEquals(ContactMatch.Found(john, "Call me ASAP"), match("John Smith Call me ASAP", allowLeftover = true))

    @Test fun noLeftoverUnlessAllowed() = assertEquals(ContactMatch.NotFound, match("mom running late"))
}
