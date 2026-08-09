- What you changed and why. Ans: 
1. Add maximum redeem per user
2. update api for maximum redeem per user (added in README.md)
3. handle concurrent to avoid exceed maximum redeem per user
- Anything you noticed but deliberately **did not** change, and your reasoning. Ans:
1. Separate the campaign limitation to another table, but lack of time
2. Handle the concurrent by using versioning, but lack of time
- What you'd do next if you had another day. Ans:
1. Separate the campaign limitation to another table
2. Handle the concurrent by using versioning
Plus three short reflection answers:
- What did you get wrong first, and how did you notice?. Ans:
1. The status "VOID" meant something, shouldn't including inside the "max redemptions per user"
- Which AI suggestion did you reject, and why?
1. Complicated test case, I think the test case should be as simply as possible
- What took you longest?
1. Understand the user need