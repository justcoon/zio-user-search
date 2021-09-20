

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