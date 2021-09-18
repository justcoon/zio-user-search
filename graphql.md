
```
query searchUsers() {
  searchUsers(query:"UK", page: 0, pageSize: 10) {
    count
    items {
      id
      email
      address {
        country
      }
    }
  }
}

```