package com.github.sanity.shoebox.demos.one

import com.github.sanity.shoebox.Store
import com.github.sanity.shoebox.View
import java.nio.file.Files

/**
 * Created by ian on 3/9/17.
 */

fun main(args : Array<String>) {
    val dir = Files.createTempDirectory("sb-")
    val userStore = Store(dir.resolve("users"), User::class)
    val usersByEmail = View(dir.resolve("usersByEmail"), userStore, viewBy = User::email)
    val usersByGender = View(dir.resolve("usersByGender"), userStore, viewBy = User::gender)

    userStore["ian"] = User("Ian Clarke", "male", "ian@blah.com")
    val fredUser = User("Fred Smith", "male", "fred@blah.com")
    userStore["fred"] = fredUser
    userStore["sue"] = User("Sue Smith", "female", "fred@blah.com")

    println(usersByEmail["ian@blah.com"])   // Will print setOf(User("Ian Clarke", "ian@blah.com"))
    println(usersByGender["male"])          // Will print setOf(User("Ian Clarke", "ian@blah.com"),
                                            //                  User("Fred Smith", "male", "fred@blah.com"))

    usersByGender.onAdd("male", {kv ->
        println("${kv.key} became male")
    })
    usersByGender.onRemove("male", {kv ->
        println("${kv.key} ceased to be male")
    })

    userStore["fred"] = fredUser.copy(gender = "female") // Will print "fred ceased to be male"
}

data class User(val name : String, val gender : String, val email : String)