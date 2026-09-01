---
status: accepted
---

# Organization invitations are bound to a specific email, delivered out-of-band, and require an existing account to accept

An ADMIN inviting a member specifies the invitee's email address up front; the resulting invitation can only be accepted by a user already authenticated as that exact email (case-insensitive). No email is actually sent — the ADMIN copies the invite link/token from the UI and shares it manually (Slack, chat, etc.), the same way [0003](0003-no-real-authentication-in-mvp.md) already accepts no email verification on signup. Accepting requires the invitee to already hold an account with the matching email; there is no "you have a pending invitation" surfacing at signup or login, and no support for inviting an email that hasn't signed up yet.

This was a real fork: the alternative was an email-agnostic shareable link/code redeemable by any logged-in user who has it (Slack/Notion-style workspace invite links), which would be simpler to implement and wouldn't require the invitee to already have an account. Binding to an email was chosen because it matches how the rest of the app already treats email as the identity anchor (signup, login), and prevents an invite link leaking beyond its intended recipient even without real delivery infrastructure. The cost is a rougher edge for a genuinely new user: they must sign up first, then separately revisit the invite link to accept — no `next=`-style post-login redirect exists anywhere in this app yet, so this step didn't add one just for invitations. Revisit if self-service signup-during-invite-acceptance becomes a real product need.
