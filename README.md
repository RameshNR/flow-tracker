# Flow Tracker



NOTE: Before the first build, set your own IntelliJ IDEA installation path in `build.gradle.kts` (see [Find your IntelliJ IDEA installation path](#find-your-intellij-idea-installation-path)).



## Overview



Flow Tracker is an IntelliJ IDEA plugin by **Shopping Team 7** for following a code path through a Java, Kotlin or Java web project (JSP, JavaScript, TLD and more). While you read code, you click the lines that matter (or let the debugger record them). Flow Tracker collects them in a side panel (grouped by file and method, or as one flat sequence), keeps them in **call order** automatically, and copies the whole flow as text, a compact log or a Markdown document. Flows have names, can be exported as `.flow.json` files to share with your team, and can be replayed step by step in the editor.

This is a Java Gradle project built with **JDK 25** against **IntelliJ IDEA 2026.2**. It uses the Gradle wrapper (`gradlew.bat`) to provide the right Gradle version, so you don't need to install Gradle.

![Grouped View](src/main/resources/images/flow-tracker-grouped-view.png)



# Prerequisites



Make sure you have the following installed:



- **IntelliJ IDEA 2026.2 or newer** (build `262+`, Community/OSS or Ultimate)
- **JDK 17 or newer** on your `PATH` / `JAVA_HOME` to run Gradle (check with `java -version`)
- **JDK 25** to compile the plugin (optional: Gradle downloads it automatically on the first build if it isn't installed)
- **Gradle 9.6.0** (optional, as this project uses the Gradle wrapper)
- **Internet access** for the first build (Gradle, plugin libraries and, if needed, JDK 25 are downloaded once)

| Component | Version | Where it is set |
|---|---|---|
| IntelliJ IDEA | 2026.2+ (`sinceBuild = 262`) | `build.gradle.kts` |
| Java (plugin code) | 25 | `build.gradle.kts` → `java.toolchain` |
| Gradle | 9.6.0 | `gradle/wrapper/gradle-wrapper.properties` |
| IntelliJ Platform Gradle Plugin | 2.19.0 | `build.gradle.kts` → `plugins` |
| Plugin version | 1.1.0 | `build.gradle.kts` → `version` |



## Installation



**Just use the plugin:** download `flow-tracker-<version>.zip` from the [latest release](https://github.com/RameshNR/flow-tracker/releases/latest) (don't unzip it), then in IntelliJ IDEA 2026.2+ go to **Settings → Plugins → ⚙️ → Install Plugin from Disk…**, pick the zip and restart the IDE.

**Build it yourself:**

- Copy or clone this repository to any folder, for example `C:/Projects/flow-tracker`
- Open `build.gradle.kts` and set `local("...")` to **your** IntelliJ IDEA installation path (see the next section):
  ```kotlin
  local("C:/Users/<you>/AppData/Local/Programs/IntelliJ IDEA OSS")
  ```
- Open PowerShell in the `flow-tracker` folder (the one that contains `gradlew.bat`)
- Start a sandbox IntelliJ with the plugin installed (the first run takes a few minutes):
  ```shell
  .\gradlew.bat runIde
  ```
- In the sandbox IDE, open a Java project, then open **Flow Tracker** from the right-hand tool window bar
- Hover a line in a source file (for example a `.java`, `.kt`, `.jsp`, `.js` or `.tld` file) and click the **+** in the gutter

To build the installable plugin instead, see [Build the plugin](#build-the-plugin).



## Find your IntelliJ IDEA installation path



The build compiles against the IntelliJ IDEA that is installed on your machine. `local("...")` in `build.gradle.kts` must point to the **installation folder**: the folder that contains `product-info.json` and the `bin` and `lib` folders.

> ⚠️ `C:/Users/<you>/AppData/Roaming/JetBrains/IdeaIC2026.2` is **not** the installation. It is IntelliJ's settings folder, and using it fails with `Cannot resolve 'product-info.json'`.

Any of these ways works:

**1. PowerShell: search the usual locations**

```powershell
Get-ChildItem "$env:LOCALAPPDATA\Programs", "C:\Program Files\JetBrains", "$env:LOCALAPPDATA\JetBrains\Toolbox\apps" `
  -Filter product-info.json -Recurse -Depth 3 -ErrorAction SilentlyContinue |
  ForEach-Object { "{0}  ->  {1}" -f (Get-Content $_.FullName -Raw | ConvertFrom-Json).version, $_.DirectoryName }
```

Each line shows a version and its folder. Pick a `2026.2.x` folder.

**2. From the running IDE (Task Manager)**

- Start IntelliJ IDEA
- Open **Task Manager** → **Details** tab → right-click `idea64.exe` → **Open file location**
- Explorer opens the `bin` folder. The installation path is its **parent** folder.

**3. From the Start menu shortcut**

- Start menu → right-click **IntelliJ IDEA** → **Open file location**
- Right-click the shortcut → **Properties** → **Target** shows `...\bin\idea64.exe`. The installation path is everything before `\bin`.

**4. JetBrains Toolbox**

- Open Toolbox → click **⋮** next to IntelliJ IDEA → **Show in Explorer** (or **Settings → Install location**)

**Typical locations**

| Installed with | Typical installation path |
|---|---|
| JetBrains installer (current user) | `C:/Users/<you>/AppData/Local/Programs/IntelliJ IDEA OSS` |
| JetBrains installer (all users) | `C:/Program Files/JetBrains/IntelliJ IDEA Community Edition 2026.2` |
| JetBrains Toolbox | `C:/Users/<you>/AppData/Local/JetBrains/Toolbox/apps/...` |

Check the folder: it must contain `product-info.json`. Write the path in `build.gradle.kts` with forward slashes `/`.



## Build the plugin



```shell
.\gradlew.bat clean buildPlugin
```

The installable plugin is written to:

```
build\distributions\flow-tracker-1.1.0.zip
```

Install it in IntelliJ IDEA:

- **Settings** → **Plugins** → ⚙️ → **Install Plugin from Disk…**
- Select `build\distributions\flow-tracker-1.1.0.zip`
- Restart the IDE when asked

To release a new version, change `version = "1.1.0"` in `build.gradle.kts` and build again.



## Commands



Open this README in IntelliJ IDEA and click the ▶ icon next to a block to run it in the terminal.

Run in a sandbox IDE:

```shell
.\gradlew.bat runIde
```

Build the plugin zip:

```shell
.\gradlew.bat clean buildPlugin
```

Quick compile check:

```shell
.\gradlew.bat compileJava
```

Run the unit tests (see [Tests](#tests)):

```shell
.\gradlew.bat test
```

Remove build output:

```shell
.\gradlew.bat clean
```

Stop Gradle background processes:

```shell
.\gradlew.bat --stop
```

On macOS/Linux use `./gradlew` instead of `.\gradlew.bat`.



## Using Flow Tracker



**Adding and removing lines**

- Hover a line in a supported file and click **+** in the gutter. Click the green marker to remove the line.
- Or right-click in the editor → **Add to Flow Tracker** (`Ctrl+Alt+Shift+A`, also under **Tools → Flow Tracker**): adds the caret line, or every line of the selection (all carets), as one undo step. On a saved line without a selection it reads **Remove from Flow Tracker**.
- Saved lines show their **step number** in the gutter (in the order the panel shows them). Hover the number for the method, tag and note.
- Saved lines follow your edits: add or remove lines above them and their line numbers move with the code (in both views, while the file is open in an editor).
- **Add Bottom ↓ / Add Top ↑**: where new lines go.
- **Undo / Redo**: revert adding, deleting and clearing lines. Hover them to see what they will change.

**Supported files**

| Files | What Flow Tracker knows about a line |
|---|---|
| `.java`, `.groovy`, `.gradle` | Class and method from the IDE's code model; Sync and the diagrams follow calls |
| `.kt`, `.kts` | Class and function (through UAST); Sync and the diagrams follow calls |
| `.js`, `.jsx`, `.mjs`, `.cjs`, `.ts`, `.tsx` | Enclosing class and function, or the callback it is in, e.g. `addEventListener("submit") callback`. Calls between saved functions (e.g. a submit handler calling `validate()`) get arrows |
| `.jsp`, `.jspf`, `.jspx`, `.tag`, `.tagx` | The method in a `<%! %>` declaration, `[scriptlet]`, `[script]`, or the enclosing element, e.g. `form#searchForm`. Calls from the page's Java code (`<% %>`, `<%! %>`, `<%= %>`) to saved Java methods get arrows |
| `.tld` | The tag or function, e.g. `tag status`, with the library's `short-name` as the class |
| `.xml`, `.html`, `.htm`, `.xhtml` | The nearest element with an `id` / `name`, else the nearest element |
| `.properties`, `.yaml`, `.yml`, `.sql` | YAML: the parent keys, e.g. `spring.datasource`; the others: the file only |

Lines outside methods are grouped as `[fields]`, `[imports]` or `[class]` (lines saved by older versions show `[class level]`).

IntelliJ IDEA Community has no parser for JavaScript or JSP, so for web and config files Flow Tracker reads the text. Names are a best effort. Calls from JSP Java code are matched to saved Java methods by name and number of arguments (for example `new DestinationService().search(destination, budget, days)` links the page to `DestinationService.search()`); a name shared by several saved methods with the same number of arguments gets no arrow rather than a wrong one. Calls in JavaScript are matched to saved JavaScript functions by name (a name saved in several files links to the one in the same file). Calls from TLD, XML and config files aren't followed.

**Recycle Bin**

- Everything you delete goes to the Recycle Bin: single lines (from the panel or the gutter), **Clear**, and deleted flows. Open it from **Settings ⚙️ → Recycle Bin**.
- Select items to see their lines, then **Restore**. Lines go back into the flow they came from, at their old places (or into the current flow if that flow is gone). A deleted flow comes back as a flow of its own.
- **Delete Permanently** (or the `Delete` / `Backspace` key) removes the selected items for good, after asking; **Empty Recycle Bin** removes everything. Double-click an item to restore it.
- Undoing a delete takes it back out of the bin. The bin is saved with the project and keeps the latest 500 items.

**Record while debugging**

- Click **Record**, then debug as usual. Every line where the debugger stops (a breakpoint or a step) is added at the bottom, in the order it ran. Click **Recording** to stop.
- It also picks up debug sessions that are already running. Recording turns Sync off so the execution order is kept.

**Flows (and sharing them)**

- Each project can hold as many flows as you like. The flow button at the top left (for example `Flow 1: Checkout bug ▾`) opens the **Flows** popup: click a flow to show it. The current flow is checked, and every flow shows its line count.
- **Reorder**: drag a flow by its handle (⠿), or select it and press `Ctrl+Up` / `Ctrl+Down`. **Copy All Flows** copies the flows in this order.
- **New Flow…** and **Rename Flow…** start the name as `Flow 1: ` (with the flow's number) and put the cursor after it, so you only type the name, e.g. `Flow 1: Checkout bug`. You can delete the prefix if you don't want it. Leaving just the prefix keeps the plain `Flow 1`.
- **Delete Flow** moves the flow to the Recycle Bin, so it can be restored. Deleting the only flow empties it instead.
- **Export Flow…** saves the current flow as a `.flow.json` file. Paths are stored relative to the project folder, so you can commit the file or send it to a teammate.
- **Import Flow…** opens a `.flow.json` file as a new flow. Files are found by relative path first, then by file name. Lines whose file isn't in the project are marked *file missing*.
- Undo / Redo history belongs to the flow it was made in and starts empty when you switch.

**Search**

- The search box above the toolbar shows only lines whose code, file, class, method, note, project or tag contains the text (or whose line number matches).
- While a filter is on, **Copy** copies only the matching lines. Moving lines still works: hidden lines keep their places.

**Views**

- **Grouped View**: file → method → lines, with a project tag on every file.
- **Flat View**: one numbered sequence in flow order.

![Flat View](src/main/resources/images/flow-tracker-flat-view.png)

**Rows**

- Hover a row for its action bar: `↑ ↓ | Copy Note | Delete`. Arrows appear only when the row can move, and Note only on code lines.
- Drag to reorder. Right-click for Move, Copy, Note, Jump to Source, Walk from Here, Tag and Delete. Double-click a line to open it.
- `Ctrl+C` copies the selection, `Delete` removes it.
- **Tags**: right-click → **Tag** → 🐞 Bug, ⭐ Important or ❓ Question (or No Tag). Tags show as coloured labels and are included in every copy format.
- **Out-of-date lines**: when a saved line no longer matches the code, it gets an orange warning: *file missing*, *moved to line N* or *code changed*. Hover it for details. Right-click → **Re-link to Code** follows moved code, or takes the line's current code. **Re-link All Moved Lines** fixes every moved line at once.

![Hover action bar](src/main/resources/images/flow-tracker-hover-actions.png)

**Walk-through**

- **Next Step** / **Previous Step** (toolbar arrows, or `Alt+]` / `Alt+[` anywhere in the IDE) open each saved line in the editor in the order the panel shows them. A hint shows the step number, the method, the tag and the note.
- Select a line, or right-click → **Walk from Here**, to continue from it. The actions are also under **Tools → Flow Tracker**, and you can rebind the shortcuts in **Settings → Keymap**.

**Sync (call order)**

- With **Sync** on, a method comes first and the methods it calls follow right after the calling line, across files.
- Moving anything by hand turns Sync off. Click **Sync** again to re-sort.

**Settings (⚙️)**

| Option | What it does |
|---|---|
| Sync Files | Automatic call order |
| Optimize Flat View Log | Flat View copy as a compact `[project]` / file / method log with `line \| code` rows |
| Copy as Markdown | Copy as a `.md` document: summary table, Mermaid diagrams of the real calls, each method's lines as a table with syntax-highlighted code, and the **Original Log** at the end (see below) |
| Sequence Diagram | In the Markdown copy, draw the calls as a Mermaid `sequenceDiagram` (files calling each other, with returns) instead of a `flowchart`. It follows the real calls from each entry method, whatever order the lines are listed in |
| Copy All Flows | When on, the toolbar's **Copy** button copies every flow, one after the other under its name, in the Flows popup's order and the copy format chosen above. `Ctrl+C` still copies the selected lines |
| Recycle Bin | Restore or permanently delete deleted lines and flows |
| Info | Help dialog with all features, with a carousel of screenshots at the top |

*Optimize Flat View Log* and *Copy as Markdown* exclude each other.

**Open Markdown** (the toolbar button next to **Copy**) opens the flow as a Markdown document in the editor, so the preview shows the diagrams right away: always as Markdown, whatever the copy setting, and all flows when *Copy All Flows* is on. The document is a scratch file (**Project view → Scratches and Consoles**), so you can keep it, edit it or save it elsewhere.

In the Markdown copy, each method's lines are a table with the line number in its own column, the code (syntax highlighted), and a Note column (tag and note) when any line has one:

| Line | Code | Note |
|---:|---|---|
| 21 | <code><span style="color:#3574F0;font-weight:600">if</span> (customerName == <span style="color:#3574F0;font-weight:600">null</span> &#124;&#124; customerName.isBlank()</code> | 🐞 Bug · _Storing the Data_ |
| 27 | <code><span style="color:#3574F0;font-weight:600">if</span> (nights &gt;= travel.getDurationDays()) {</code> |  |

The code is colored with the same rules as the panel (keywords, types, strings, numbers, comments; tags and attribute values in markup) using inline HTML colors. IntelliJ's Markdown preview, VS Code and most editors show the colors; GitHub removes inline colors and shows the same table with plain code.

In the Markdown diagrams, each box or column is titled with the file name and extension (for example `BookingService.java`, `travel.js`, `travel.tld`). Code that doesn't call between groups (for example a Java service, a script and a tag library) gets one diagram per group; a JSP page that calls Java methods is in the same diagram as them. Lines outside methods (fields, imports) are left out of the diagrams but stay in the details.

Each flow's Markdown ends with an **Original Log** section: the plain copy of the current view in a text block, i.e. the indented text in Grouped View or the numbered steps in Flat View (exactly what **Copy** gives with *Copy as Markdown* off). With **Copy All Flows** on, every flow ends with its own Original Log.

```text
1. BookingService.bookTrip():21 | if (customerName == null) {
   note: Storing the Data
2. HotelService.calculateCost():34 | return nights * rooms;
```

A Markdown copy with the sequence diagram, rendered in IntelliJ's Markdown preview:

![Markdown copy](src/main/resources/images/flow-tracker-markdown-copy.png)

![Settings](src/main/resources/images/flow-tracker-settings.png)

Flows, settings and the Recycle Bin are saved per project in `.idea/workspace.xml`. Lines saved by version 1.0.0 open as **Flow 1**. The same help is in the plugin under **Settings ⚙️ → Info**.



## Screenshots



The image carousel at the top of a plugin's page in **Settings → Plugins** comes from **JetBrains Marketplace**: it shows the screenshots uploaded on the plugin's Marketplace page (**Edit plugin → Media**). It can't be set in `plugin.xml`, and a plugin installed from disk shows no carousel. To get one:

1. Publish the plugin on [JetBrains Marketplace](https://plugins.jetbrains.com) (see [Publish to JetBrains Marketplace](#publish-to-jetbrains-marketplace)).
2. On the plugin's Marketplace page, open **Edit → Media** and upload the screenshots (PNG or JPG; JetBrains recommends 1280×800). Their order there is the carousel order.
3. The IDE shows the carousel for everyone who views the plugin in **Settings → Plugins → Marketplace**.

**Plugin icon.** The icon in **Settings → Plugins** is `src/main/resources/META-INF/pluginIcon.svg` (40×40) and `pluginIcon_dark.svg` for dark themes. IntelliJ shows a generic plug icon when they're missing.

**Images in the Overview tab.** The text on the plugin's **Overview** tab comes from `src/main/plugin-description.html` (Gradle puts it into `plugin.xml`). It has a spot for each screenshot, but the IDE only shows description images from a web address, so they appear once the images are online:

1. Put the files from `src/main/resources/images/` in a public web folder, for example by pushing this project to a public GitHub repository.
2. Set that folder's address in `gradle.properties`:
   ```properties
   pluginImagesUrl=https://raw.githubusercontent.com/<owner>/<repo>/main/src/main/resources/images
   ```
3. Rebuild the plugin and install it again:
   ```shell
   .\gradlew.bat clean buildPlugin
   ```

While `pluginImagesUrl` is empty, the description is built without images (no broken pictures). The images show one below another in the text; a sliding carousel there needs JetBrains Marketplace (below).

Inside the plugin, **Settings ⚙️ → Info** opens with a carousel of the screenshots in `src/main/resources/images/` (dots, arrows, the Left / Right keys, and it moves on every few seconds unless the mouse is over it).

This README and the Info carousel use these images. Replace them with the same names to update them, and use the same files for the Marketplace carousel:

| File | Shows |
|---|---|
| `flow-tracker-grouped-view.png` | Grouped View with files, methods and code blocks |
| `flow-tracker-flat-view.png` | Flat View with step numbers |
| `flow-tracker-hover-actions.png` | The hover action bar on a row |
| `flow-tracker-settings.png` | The settings popup |
| `flow-tracker-markdown-copy.png` | A Markdown copy rendered with the flow diagram |

The Info carousel also shows these when they exist (optional; add them with exactly these names):

| File | Shows |
|---|---|
| `flow-tracker-flows-popup.png` | The Flows popup with named flows and drag handles |
| `flow-tracker-walk-through.png` | The walk-through hint in the editor ("Step 2 / 6") |
| `flow-tracker-record.png` | Record on, with the debugger paused |
| `flow-tracker-recycle-bin.png` | The Recycle Bin with an item selected |
| `flow-tracker-markdown-table.png` | A Markdown copy with the line table and the Original Log |
| `flow-tracker-jsp-flow.png` | A diagram with a JSP page calling Java methods |



## Publish to JetBrains Marketplace



The build is set up for publishing; the secrets come from environment variables, never from files in the project.

1. Create an account on [JetBrains Marketplace](https://plugins.jetbrains.com) and a **permanent token** (**Profile → My Tokens**).
2. Optional but recommended: create a signing certificate ([Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)).
3. In PowerShell, set the variables for the current window:
   ```powershell
   $env:PUBLISH_TOKEN = "<your Marketplace token>"
   $env:CERTIFICATE_CHAIN = Get-Content -Raw chain.crt      # only when signing
   $env:PRIVATE_KEY = Get-Content -Raw private.pem          # only when signing
   $env:PRIVATE_KEY_PASSWORD = "<key password>"             # only when signing
   ```
4. Publish (the first upload of a new plugin can also be done by hand on the Marketplace website with the zip from `buildPlugin`):
   ```shell
   .\gradlew.bat publishPlugin
   ```
5. On the plugin's Marketplace page, add the screenshots under **Edit → Media** for the carousel, and a vendor e-mail / URL if the Marketplace asks for them.



## Tests



`src/test` holds JUnit 5 tests for everything that doesn't need a running IDE: the Markdown diagrams and line tables, the Original Log, reading JSP / JavaScript / TLD files (with sample files in `src/test/resources/fixtures`), JSP → Java and JavaScript calls, the Recycle Bin and undo, flows and their order, copy formats, labels, file types and highlighting.

```shell
.\gradlew.bat test
```

The report is written to `build\reports\tests\test\index.html`.



## Troubleshooting



| Problem | Fix |
|---|---|
| `Cannot resolve 'product-info.json'` | `local(...)` doesn't point to an IntelliJ installation. See [Find your IntelliJ IDEA installation path](#find-your-intellij-idea-installation-path). |
| `'.\gradlew.bat' is not recognized` | Run the command in the folder that contains `gradlew.bat`. |
| Java 25 / toolchain download fails | Check internet access (needed once) or install JDK 25 yourself. |
| Flow Tracker isn't visible in the sandbox | **View → Tool Windows → Flow Tracker**. |
| Sync doesn't link two methods | Both methods need a saved line. Set a Project JDK in the sandbox project (*Setup SDK*). |
| Lines show *moved to line N* | Lines follow edits made in an open editor. Changes made elsewhere (a Git pull, another tool, a closed file) can move code away from its saved line: right-click → **Re-link All Moved Lines**. |
| Record adds nothing | Recording only adds lines in supported files, and only when the debugger actually stops (a breakpoint or a step). |
| A diagram arrow is missing | Arrows link only methods that have a saved line, and only calls from Java, Kotlin, Groovy or JSP Java code. Set a Project JDK so calls resolve; Sync falls back to method names (and, for JSP, name plus number of arguments) when they don't. A name shared by several saved methods gets no arrow. |
| `Alt+]` / `Alt+[` / `Ctrl+Alt+Shift+A` do nothing | Another plugin or keymap may use them. Rebind **Next Flow Step** / **Previous Flow Step** / **Add to Flow Tracker** in **Settings → Keymap**. |
| The sandbox IDE misbehaves after many runs | Close it, delete `.intellijPlatform\sandbox`, and run the sandbox again (see [Commands](#commands)). |
