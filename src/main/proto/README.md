![Buf Lint](https://github.com/nicolaspayette/surimi_protocol/actions/workflows/buf.yml/badge.svg)

# SURIMI protocol files

Protobuf definitions for inter-model communication within the [Surimi project](https://www.surimi-project.eu/).

## Style guide

We use [buf](https://github.com/bufbuild/buf) to enforce standards and formatting.

We follow the [`STANDARD`](https://buf.build/docs/lint/rules/#standard) linting rules.

There are [plugins](https://buf.build/docs/cli/editor-integration/) to run the linter from your editor of choice, but you can also simply run `buf lint` in the terminal from the repo's root folder.

To apply autoformatting, simply run `buf format -w`.

We are currently adopting a pragmatic approach regarding the [1-1-1 rule](https://protobuf.dev/best-practices/1-1-1/). We adhere strictly to the "one service per file" bit, but we allow messages to be defined in the same file as their service if they are strictly `*Request`/`*Response` messages. Messages that can be used by multiple services _must_ be defined in their own files.

We also allow a message of one type and a message with a list of messages of that type to exist in the same file.
