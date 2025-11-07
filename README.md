# Set Card Game Project

A complete desktop implementation of the popular card game "Set," written in Java. The project features a Swing-based Graphical User Interface (GUI), support for multiple players, and multithreading for game management.

This project was built as an academic assignment, demonstrating object-oriented programming principles, separation of concerns, and thread management in a Java environment.


## 🚀 Key Features

* **Full "Set" Gameplay:** Complete logic for identifying valid sets on the board.
* **Graphical User Interface (GUI):** An interactive Java Swing interface to display the board, cards, scores, and timer.
* **Multiplayer Support:** Supports multiple human players and/or computer-controlled players (bots).
* **Flexible Configuration:** All game settings (player count, key bindings, timings, etc.) are fully configurable via an external `config.properties` file.
* **Input Management:** Separate keyboard mappings for each human player.
* **Advanced Game Management:** Includes a scoring system, penalties (freezing a player after an incorrect set), and a thread-based game timer.
* **Logging:** Detailed event logging to a log file for analysis and debugging purposes.

## 🛠️ Technologies Used

* **Java**: The core programming language.
* **Java Swing**: Used to create the Graphical User Interface (GUI).
* **Java AWT**: For event handling and UI components.
* **Java Concurrency**: For managing threads (Dealer, Players, Timers).

## 📁 Project Structure

Here is a brief overview of the main classes and interfaces in the project:

* `Main.java`: The application's entry point. It initializes all components (Config, Logger, UI, Env, Entities) and starts the main game thread (Dealer).
* `Config.java`: Loads and manages all game settings from the `config.properties` file.
* `Env.java`: A simple container (for Dependency Injection) that holds references to shared components (like Logger, Config, UI, Util) to avoid passing many parameters.
* `UserInterface.java` (Interface): Defines all actions the GUI must provide (placing cards, removing cards, updating scores, etc.).
* `UserInterfaceImpl.java` (Implementation): The Java Swing implementation of the GUI.
* `Util.java` (Interface): Defines the game's logical utilities.
* `UtilImpl.java` (Implementation): Contains the logic for checking sets (`testSet`), finding sets (`findSets`), and converting cards to features.
* `InputManager.java`: Listens for keyboard events and translates them into actions for specific players.
* `WindowManager.java`: Listens for window events (like clicking the close button) and performs a graceful shutdown of the game.
* **`ex` Package (Entities):**
    * `Dealer.java`: (Thread) Responsible for managing the game flow, dealing cards, checking submitted sets, and updating the UI.
    * `Player.java`: (Thread) Represents a player (human or bot), managing their input queue, actions, and freeze state.
    * `Table.java`: Represents the game table, holds the state of the board (which cards are in which slots), and synchronizes access to it.