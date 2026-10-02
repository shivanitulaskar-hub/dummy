# dummy

A practice repository for learning Git and GitHub.

## What's inside

| Folder | Description |
|---|---|
| [`the-console-adda/`](the-console-adda/) | A [Next.js](https://nextjs.org) app (React + TypeScript + Tailwind CSS) |

## Running the app

```bash
cd the-console-adda
npm install
npm run dev
```

Then open [http://localhost:3000](http://localhost:3000).

## Git workflow cheat sheet

```bash
git checkout main && git pull         # start from the latest main
git checkout -b my-change             # create a branch for your work
# ...edit files...
git status                            # see what changed
git add <files>                       # stage changes
git commit -m "Describe the change"   # save a snapshot
git push -u origin my-change          # upload the branch to GitHub
```

Then open a **pull request** on GitHub, review the changes, and merge into `main`.
