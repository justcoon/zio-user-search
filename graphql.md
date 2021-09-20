

```qraphql
query searchUsers() {
  searchUsers(query:"UK", page: 0, pageSize: 10, sorts:[{
    field: "email"
    asc:true
  }]) {
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