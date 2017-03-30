# android-inquiry

[![jitpack.io](https://www.jitpack.io/v/com.heinrichreimersoftware/inquiry.svg)](https://www.jitpack.io/#com.heinrichreimersoftware/inquiry)
[![Build Status](https://travis-ci.org/heinrichreimer/android-inquiry.svg?branch=master)](https://travis-ci.org/heinrichreimer/android-inquiry)
[![Apache License 2.0](https://img.shields.io/github/license/heinrichreimer/material-intro.svg)](https://github.com/heinrichreimer/android-inquiry/blob/master/LICENSE.md)

**DEPRECATED:** This library won't be continued as most of the features are now part of the original library.

*This is a forked and improved version of [Aidan Follestad's](https://github.com/afollestad) awesome library [Inquiry](https://github.com/afollestad/inquiry).  
Credit goes to him for the idea of annotation based automatic SQLite database modification :sweat_smile:.*

Inquiry is a simple library for Android that makes construction and use of SQLite databases super easy.

Read and write class objects from tables in a database and supports deep object insertion. Let Inquiry handle the heavy lifting.

## Dependency

*Inquiry* is available on [**jitpack.io**](https://www.jitpack.io/#com.heinrichreimersoftware/android-inquiry)

### Gradle dependency:

Project `build.gradle`:
```gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

Module `build.gradle`
```gradle
dependencies {
    compile 'com.heinrichreimersoftware:android-inquiry:3.0.4'
}
```

## Table of Contents

1. [Dependency](#dependency)
1.  [Quick Setup](#quick-setup)
1.  [Example Row](#example-row)
1.  [References](#references)
1.  [Converters](#converters)
1.  [Querying Rows](#querying-rows)
    1. [Basics](#basics)
    1.  [Where](#where)
    1.  [Sorting and Limiting](#sorting-and-limiting)
1.  [Inserting Rows](#inserting-rows)
1.  [Updating Rows](#updating-rows)
    1. [Basics](#basics-1)
    1.  [Updating Specific Columns](#updating-specific-columns)
1.  [Deleting Rows](#deleting-rows)
1.  [Dropping Tables](#dropping-tables)
1.  [Accessing Content Providers](#accessing-content-providers)
1.  [Changelog](#changelog)
1.  [Open source libraries](#open-source-libraries)
1.  [License](#license)

## Quick setup

When your app starts, you need to initialize Inquiry. `Inquiry.init()` and `Inquiry.deinit()` can be used from anywhere, but a reliable place to do so is in an Activity:

```java
public class MainActivity extends AppCompatActivity {

    @Override
    public void onResume() {
        super.onResume();
        Inquiry.init(this, "myDatabase", 1);
    }

    @Override
    public void onPause() {
        super.onPause();
        Inquiry.deinit();
    }
}
```

`Inquiry.init()` takes a `Context` in the first parameter, and the name of the database that'll you be using
in the second parameter. The third parameter is the database version, which could always be '1' if you want. 
Incrementing the number will drop tables created with a lower number next time they are accessed.

Think of a database like a file that contains a set of tables (a table is basically
a spreadsheet; it contains rows and columns).

When using the singleton you should use `Inquiry.get()` to obtain the current instance.

When your app is done with Inquiry, you *should* call `Inquiry.deinit()` to help clean up references and avoid memory leaks.

(You can initialize multiple Inquiry instances too by using `new Inquiry(this, "myDatabase", 1)` and `inquiry.destroy()`)

## Example row

In Inquiry, a row is just an object which contains a set of values that can be read from and written to
a table in your database.

```java
@Table
public class Person {
    // Default constructor is needed so Inquiry can auto construct instances
    public Person() {}

    public Person(String name, int age, float rank, boolean admin) {
        this.name = name;
        this.age = age;
        this.rank = rank;
        this.admin = admin;
    }

    @Column public String name;
    @Column public int age;
    @Column public float rank;
    @Column public boolean admin;
}
```

Notice that the class is annotated with the `@Table` annotation and all the fields are annotated with the `@Column` annotation.
If you have classes/fields without that annotations, they will be ignored by Inquiry.

Notice that the `@Table` and `@Column` annotation can be used with optional parameters:

`@Table` or `@Table("table_name")`
`@Column`, `@Column("column_name")` or `@Column(value = "column_name", unique = true, notNull = true, autoIncrement = true)`

* `value` indicates a table/column name, if the name is different than what you name the class field.
* `primaryKey` indicates its column is unique.
* `notNull` indicates that you can never insert null as a value for that column.
* `autoIncrement` indicates that you don't manually set the value of this column. Every time
you insert a row into the table, this column will be incremented by one automatically. This can
only be used with `INTEGER` columns (`short`, `int`, or `long` fields), however.

## References

In addition to saving primitive data types, Inquiry will also save fields that point to a class annotated with `@Table`. 

Let's take the `Person` class from the previous section, but simplify it a bit:

```java
@Table
public class Person {
    // Default constructor is needed so Inquiry can auto construct instances
    public Person() {}

    public Person(String name) {
        this.name = name;
    }

    @Column public String name;
}
```

```java
@Table
public class LovingPerson extends Person {
    // Default constructor is needed so Inquiry can auto construct instances
    public LovingPerson() {}

    public LovingPerson(String name, Person spouse) {
        super(name)
        this.spouse = spouse;
    }

    @Column public Person spouse;
}
```

**During insertion** of a `LovingPerson`, Inquiry will insert the `spouse` Field into the persons table. The value of 
the `spouse` column in the current table will be set to the ID of the new row in the persons table.

**During querying**, Inquiry will detect the reference from the `@Column` annotation, and do an automatic lookup for you.
The value of the `spouse` field is automatically pulled from the second table into the current table.

Basically, this allows you to have non-primitive column types that are blazing fast to insert or query. 
No serialization is necessary. You can even have two rows which reference the same object (a single object 
with the same ID).

**Pro Tip:** This example showcases class inheritance too. All `@Column`'s from `Person` get inherited to `LovingPerson`.

**Attention:** Make sure you don't create looping back references when using the reference feature.

## Converters

Inquiry internally uses `Converter`s to convert some basic Java types to insertable `ContentValue`s.

Currently Inquiry can automatically convert the following types:

* Java primitives (including `String`)
* References (see above)
* `byte[]`
* `char[]`
* `Character[]`
* `Bitmap`
* `Serializable`

If you need to convert other objects you can simply add your own converter:

```java
Person[] result = Inquiry.get()
    .addConverter(new CustomConverter())
    ...
```

## Querying rows

### Basics

Querying retrieves rows, whether its every row in a table or rows that match a specific criteria.
Here's how you would retrieve all rows from the type `Person`:

```java
Person[] result = Inquiry.get()
    .select(Person.class)
    .all();
```

If you only needed one row, using `one()` instead of `all()` is more efficient:

```java
Person result = Inquiry.get()
    .select(Person.class)
    .one();
```

You can also perform the query on a separate thread using a callback:

```java
Inquiry.get()
    .select(Person.class)
    .all(new GetCallback<Person>() {
        @Override
        public void result(Person[] result) {
            // Do something with result
        }
    });
```

Inquiry will automatically fill in your `@Column` fields with matching columns in each row of the table.

### Where

If you wanted to find rows with specific values in their columns, you can use `where()` selection:

```java
Person[] result = Inquiry.get()
    .select(Person.class)
    .where("name = ?", "Aidan")
    .where("age = ?", 20)
    .all();
```

The first parameter is a string, specifying the condition that must be true.
The question marks are placeholders, which are replaced by the values you specify in the second comma-separated
vararg (or array) parameter.
If you set more than one `where()` selections they get chained using `AND`, so the example above is actually the same as this:

```java
.where("name = ? AND age = ?", "Aidan", 20)
```

---

If you wanted, you could skip using the question marks and only use one parameter:

```java
.where("name = 'Aidan' AND age = 20");
```

*However*, using the question marks and filler parameters can be easier to read if you're filling them in
with variables. Plus, this will automatically escape any strings that contain reserved SQL characters.


Inquiry includes a convenience method called `atPosition()` which lets you perform operations on a specific row
in your tables:

```java
Person result = Inquiry.get()
    .select(Person.class)
    .atPosition(24)
    .one();
```

Behind the scenes, it's using `where()` to select the row. `atPosition()` moves to a row position 
and retrieves the row's ID.

### Sorting and limiting

This code would limit the maximum number of rows returned to 100. It would sort the results by values
in the "name" column, in descending (Z-A, or greater to smaller) order:

```java
Person[] result = Inquiry.get()
    .select(Person.class)
    .limit(100)
    .sort("name DESC")
    .sort("age ASC")
    .all();
```

If you understand SQL, you'll know you can specify multiple sort parameters separated by commas (or by using multiple `sort()` conditions).

```java
.sort("name DESC, age ASC");
```

The above sort value would sort every column by name descending (large to small, Z-A) first, *and then* by age ascending (small to large).

## Inserting rows

Insertion is pretty straight forward. This inserts three `People`:

```java
Person one = new Person("Waverly", 18, 8.9f, false);
Person two = new Person("Natalie", 42, 10f, false);
Person three = new Person("Aidan", 20, 5.7f, true);

Long[] insertedIds = Inquiry.get()
        .insert(Person.class)
        .values(one, two, three)
        .run();
```

Inquiry will automatically pull your `@Column` fields out and insert them into the table defined by `@Table`.

Like `all()`, `run()` has a callback variation that will run the operation in a separate thread:

```java
Inquiry.get()
    .insert(Person.class)
    .values(one, two, three)
    .run(new RunCallback() {
        @Override
        public void result(Long[] insertedIds) {
            // Do something
        }
    });
```

An ID will be added and incrementet automatically.

## Updating rows

### Basics

Updating is similar to insertion, however it results in changed rows rather than new rows:

```java
Person two = new Person("Natalie", 42, 10f, false);

Integer updatedCount = Inquiry.get()
    .update(Person.class)
    .values(two)
    .where("name = ?", "Aidan")
    .run();
```

The above will update all rows whose name is equal to *"Aidan"*, setting all columns to the values in the `Person`
object called `two`. If you didn't specify `where()` args, every row in the table would be updated.


(Like querying, `atPosition(int)` can be used in place of `where(String)` to update a specific row.)

### Updating specific columns

Sometimes, you don't want to change every column in a row when you update them. You can choose specifically
what columns you want to be changed using `onlyUpdate`:

```java
Person two = new Person("Natalie", 42, 10f, false);

Integer updatedCount = Inquiry.get()
    .update(Person.class)
    .values(two)
    .where("name = ?", "Aidan")
    .onlyUpdate("age", "rank")
    .run();
```

The above code will update any rows with their name equal to *"Aidan"*, however it will only modify
the `age` and `rank` columns of the updated rows. The other columns will be left alone.

## Deleting rows

Deletion is simple:

```java
Integer deletedCount = Inquiry.get()
    .delete(People.class)
    .where("age = ?", 20)
    .run();
```

The above code results in any rows with their age column set to *20* removed. If you didn't
specify `where()` args, every row in the table would be deleted.

(Like querying, `atPosition(int)` can be used in place of `where(String)` to delete a specific row.)

## Dropping tables

Dropping a table means deleting it. It's pretty straight forward:

```java
Inquiry.get()
    .dropTable(People.class);
```

Just pass the data type name, and it's gone.

## *Accessing Content Providers*

*Accessing content providers has been removed from this fork of Inquiry.
If you need to access content providers, I highly recommend you to check out the [original library](https://github.com/afollestad/inquiry).*

## Changelog

See the [releases section](https://github.com/heinrichreimer/inquiry/releases) for a detailed changelog.

Open source libraries
-------

Inquiry is based on [Aidan Follestad's](https://github.com/afollestad) awesome library [Inquiry](https://github.com/afollestad/inquiry)
which is licensed under the **Apache License 2.0**.

License
-------

```
Copyright 2016 Heinrich Reimer

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
