# UI Wireframes for ГеоИнфоСистема

This document provides text-based wireframes to illustrate the user interface structure and layout for the main screens of the application, based on the technical specification.

---

### 1. Login Page (`/login`)

**Purpose:** Allows users to authenticate to access the system.

```
+--------------------------------------------------+
|                                                  |
|             ГеоИнфоСистема                       |
|             ----------------                       |
|                                                  |
|    Email:                                        |
|    +------------------------------------------+  |
|    | user@example.com                         |  |
|    +------------------------------------------+  |
|                                                  |
|    Password:                                     |
|    +------------------------------------------+  |
|    | ************                             |  |
|    +------------------------------------------+  |
|                                                  |
|    [ Forgot Password? ]                          |
|                                                  |
|    +------------------------------------------+  |
|    |                  LOGIN                   |  |
|    +------------------------------------------+  |
|                                                  |
|    Don't have an account? [ Register ]           |
|                                                  |
+--------------------------------------------------+
```

---

### 2. Main Application View (`/`)

**Purpose:** The core interface for interacting with map data. It consists of a header, a main content area with the map, and two sidebars for navigation/layers and details.

```
+------------------------------------------------------------------------------------------------+
| [Logo] ГеоИнфоСистема | [ Search by name, description, docs...        ] [User Profile | Logout] |
+------------------------------------------------------------------------------------------------+
|                                                                                                |
|  +---------------------------+                               +-------------------------------+
|  | LEFT SIDEBAR              |                               | RIGHT SIDEBAR (Details)       |
|  |---------------------------|                               |-------------------------------|
|  |                           |                               |                               |
|  | Project:                  |                               |  <No object selected>         |
|  | [ My First Project  ▼ ]   |                               |                               |
|  |                           |                               |  *Click an object on the map  |
|  | --- GEO OBJECTS ---       |                               |   to see its details.*        |
|  |                           |                               |                               |
|  |  ▼ Points (15)            |                               | --- IF OBJECT IS SELECTED --- |
|  |    - Point A              |                               |                               |
|  |    - Point B              |                               |  Name: [ Transformer #123    ] |
|  |                           |                               |  Desc: [ Substation details  ] |
|  |  ▶ Lines (4)              |                               |                               |
|  |  ▶ Polygons (2)           |                               |  --- Characteristics ---       |
|  |                           |                               |  Voltage: [ 10kV             ] |
|  | [ Add Point   ] (Editor)  |                               |  Status:  [ Active            ] |
|  | [ Add Line    ] (Editor)  |                               |  [ + Add Characteristic ]      |
|  | [ Add Polygon ] (Editor)  |                               |                               |
|  | [ Import KML  ] (Editor)  |                               |  --- DOCUMENTS (3) ---         |
|  |                           |                               |  - protocol.docx [Dwn][Edt][Del] |
|  | --- IMAGERY LAYERS ---    |      MAP (OpenLayers)         |  - scheme.pdf    [Dwn]    [Del] |
|  |                           |                               |  - photo.jpg     [Dwn]    [Del] |
|  | [x] Drone Flight 2024-Q3  |                               |                               |
|  |  Opacity: [---O--]  slider|                               |  [ Upload Document ] (Editor)  |
|  |                           |                               |                               |
|  | [ ] Drone Flight 2024-Q2  |                               |  [ Save Changes ] (Editor)     |
|  |  Opacity: [------] slider|                               |  [ Delete Object  ] (Editor)     |
|  |                           |                               |                               |
|  +---------------------------+                               +-------------------------------+
|                                                                                                |
+------------------------------------------------------------------------------------------------+
```
**Interaction Notes:**
*   Clicking a geo-object in the left sidebar or on the map populates the right sidebar with its details.
*   Buttons marked with `(Editor)` are only visible to users with `EDITOR` or `ADMIN` roles.
*   The "Edit" (`[Edt]`) button next to a document opens it in OnlyOffice.

---

### 3. Admin - User Management Page (`/admin/users`)

**Purpose:** Allows administrators to manage user accounts and roles.

```
+------------------------------------------------------------------------------------------------+
| [Logo] ГеоИнфоСистема | [ Search... ]                                 [User Profile (Admin) | Logout] |
+------------------------------------------------------------------------------------------------+
|                                                                                                |
|   ADMIN PANEL                                                                                  |
|   > Users                                                                                      |
|   - System Logs                                                                                |
|                                                                                                |
|   +------------------------------------------------------------------------------------------+ |
|   |                                                                                          | |
|   |   USER MANAGEMENT                                       [ + Add New User ]               | |
|   |   --------------------------------------------------------------------                   | |
|   |                                                                                          | |
|   |   | USERNAME      | EMAIL                  | ROLE     | ACTIONS                       | | |
|   |   |---------------|------------------------|----------|-------------------------------| | |
|   |   | John Doe      | john.doe@example.com   | EDITOR   | [ Edit Role ] [ Delete User ] | | |
|   |   | Jane Smith    | jane.smith@example.com | VIEWER   | [ Edit Role ] [ Delete User ] | | |
|   |   | Admin User    | admin@example.com      | ADMIN    | [ Edit Role ] [ Delete User ] | | |
|   |                                                                                          | |
|   +------------------------------------------------------------------------------------------+ |
|                                                                                                |
+------------------------------------------------------------------------------------------------+
```
**Interaction Notes:**
*   `[ Edit Role ]` would open a modal or dropdown to change the user's role (e.g., from VIEWER to EDITOR).
*   This page is only accessible to users with the `ADMIN` role.

---
