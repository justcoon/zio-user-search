
search users example

```qraphql
query searchUsers() {
  searchUsers(query: "UK", page: 0, pageSize: 10, sorts:[{
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

suggest users example

```qraphql
query suggestUsers() {
  suggestUsers(query: "bl") {
    suggestions {
      property
      suggestions {
        text
      }
    }
  }
}
```

get user example

```qraphql
query getUser() {
  getUser(id: "123") {
      id
      email
      address {
        country
      }
      department {
        name
      }
  }  
}
```