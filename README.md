# Pubbler

Pubbler is a [TextBundle](http://textbundle.org/) publisher. You can share a
TextBundle file from [Bear.app](https://bear.app/) (or wherever) and get it
published to your static blog.

Right now it's a chat bot for Telegram which sits there and waits for a file to
arrive so it so it can publish it.


## Configuration

Set environment variables to configure application:

| var      | Description                    |
|----------|--------------------------------|
| PORT     | Port to run on (default: 9000) |
| DOMAIN   | Domain application will run on |
| TGTOKEN  | Telegram bot token             |
| PGURL    | Postgres URL to connect to     |
| GHID     | Github application id          |
| GHSECRET | Github application secret      |
