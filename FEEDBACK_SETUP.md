# Task Tunnel feedback delivery setup

Task Tunnel can send the in-app feedback form directly to Formspree. Formspree then emails each submission to the address connected to the form.

1. Create a Formspree account and a new form at https://formspree.io/.
2. Confirm the email address where you want Task Tunnel feedback delivered.
3. Copy the form ID from the endpoint Formspree gives you. For an endpoint like `https://formspree.io/f/abcdwxyz`, the form ID is `abcdwxyz`.
4. Add this line to the project-root `local.properties` file:

   `TASK_TUNNEL_FEEDBACK_FORM_ID=abcdwxyz`

5. Rebuild the APK.

`local.properties` is already gitignored. The Formspree form ID is not treated as a secret by the app; it is simply kept out of source so different builds can target different feedback inboxes.

The app sends only:
- selected feedback category
- user-written feedback
- source = Task Tunnel Android
- optional safe status fields if the user checks **Include app status**

It does not send Attention history, Drift paths, accessibility text, messages, usernames, search queries, screenshots, or app content.
