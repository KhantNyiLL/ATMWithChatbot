# VAULT_X – ATM with Chatbot  

A **mini ATM simulation system** combined with a **rule-based chatbot**, built using **JavaFX**.  
This project demonstrates basic banking operations (ATM features) and integrates a chatbot to guide users.  

---

## Project Structure  

ATMChatbotProject
├── .idea # IntelliJ IDEA project settings

├── out # Compiled output

└── src

└── main

├── java

│ └── ATMWithChatbot.java # Main JavaFX application

└── resources

├── styles.css # UI styling (JavaFX CSS)

└── VAULT_X-LOGO.png # Project logo


---

##  UI & Styling  

- **Logo**: `VAULT_X-LOGO.png` – represents a vault, symbolizing security.  
- **CSS (`styles.css`)**:
  - `root-atm` → App background  
  - `header-atm` → Header bar (gradient + border)  
  - `title-atm` → Project title (Courier New, bold)  
  - `panel-login`, `panel-left`, `panel-chat` → Rounded panels for login, ATM, chatbot  
  - `btn-atm` → Styled ATM buttons (hover, pressed, disabled states)  
  - `terminal-input` → Terminal-style input box  
  - `terminal-textarea` → Chatbot output area  

---

##  Features  

- **ATM Simulation**
  - User login
  - Balance checking
  - Deposit / Withdrawal
  - Simple transaction management  

- **Rule-based Chatbot**
  - Assists user with common ATM-related queries  
  - Provides quick guidance (like help, balance instructions, etc.)  

---

##  How to Run  

1. Open project in **IntelliJ IDEA (2025.1.3 or newer)  and other relateable IDE**
2. Make sure **JavaFX SDK** is set up in your project settings  
3. Run the main class:
4. The app will launch with the custom styles and logo.  


---

## 🔮 Future Plans  

- Add **database integration** (store real user accounts & transaction history)  
- Enhance **chatbot intelligence** (NLP / AI-based responses)  
- Improve **UI design** (responsive layout, animations)  
- Add **security features** (PIN encryption, session management)  
- Multi-user support with role-based access  

---

## 👥 Team Notes  

- Keep **styles.css** updated for consistent UI design  
- All images/logos go into **resources/**  
- Main logic should remain modular inside `ATMWithChatbot.java` or split into additional classes if project grows  

