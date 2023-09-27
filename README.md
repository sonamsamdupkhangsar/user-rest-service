# user-rest-service
This is user-rest-service for managing user data.
This service exposes a user signup api.  Please check the [workflow](#signup-user-workflow) diagrams below to see the service interaction.

## Run locally using profile
Use the following to run local profile which will pick up properties defined in the `application-local.yml` :

To run another instance on another port use :

Or you can do something like following too to specify the db server properties:

 
 
## Build Docker image

Build docker image using included Dockerfile.


`docker build -t ghcr.io/<username>/user-rest-service:latest .` 

## Push Docker image to repository

`docker push ghcr.io/<username>/user-rest-service:latest`

## Deploy Docker image locally

`docker run -e POSTGRES_USERNAME=dummy \
 -e POSTGRES_PASSWORD=dummy -e POSTGRES_DBNAME=account \
  -e POSTGRES_SERVICE=localhost:5432 \
 -e apiKey=123 -e DB_SSLMODE=DISABLE
 --publish 8080:8080 ghcr.io/<username>/user-rest-service:latest`


## Installation on Kubernetes
Use my Helm chart here @ [sonam-helm-chart](https://github.com/sonamsamdupkhangsar/sonam-helm-chart):

```
helm install user-rest-service sonam/mychart -f values-backend.yaml --version 0.1.15 --namespace=yournamespace
```

## Instruction for port-forwarding database pod
```
export PGMASTER=$(kubectl get pods -o jsonpath={.items..metadata.name} -l application=spilo,cluster-name=project-minimal-cluster,spilo-role=master -n yournamesapce); 
echo $PGMASTER;
kubectl port-forward $PGMASTER 6432:5432 -n backend;
```

### Login to database instruction
```
export PGPASSWORD=$(kubectl get secret <SECRET_NAME> -o 'jsonpath={.data.password}' -n backend | base64 -d);
echo $PGPASSWORD;
export PGSSLMODE=require;
psql -U <USER> -d projectdb -h localhost -p 6432

```
### Send post request to create user account
```
 curl -X POST -json '{"firstName": "dummy", "lastName": "lastnamedummy", "email": "yakApiKey", "authenticationId": "dummy123", "password": "12", "apiKey": "APIKEY"}' https://user-rest-service.sonam.cloud/signup
```

## Signup User workflow
```mermaid
flowchart TD
  User[user-request] --> user-rest-service[signup user]
  user-rest-service --> isAuthenticationIdUnique{is authenticationid unique?}
  isAuthenticationIdUnique -->|No, authenticationId already used| returnError[Retrun http 400 error]
  isAuthenticationIdUnique -->|Yes| accountExistsByAuthenticationIdAndTrue{Is there an account with AuthenticationId already and account created is true?}
  accountExistsByAuthenticationIdAndTrue -->|Yes, account already exists and created, check email to activate| returnError
  accountExistsByAuthenticationIdAndTrue -->|No| accountWithEmailExists{Account with email exists?}
  accountWithEmailExists -->|Yes| deleteAccount[Delete existing user that]
  deleteAccount --> deleteExistingAccount[account-rest-service]
  deleteExistingAccount -->  deleteByAuthenticationIdAndActiveFalse
  accountWithEmailExists -->|No| deleteByAuthenticationIdAndActiveFalse[delete existing user if active value false]
  deleteByAuthenticationIdAndActiveFalse --> userDb[(userDb postgresql)]
  deleteByAuthenticationIdAndActiveFalse --> saveUser[create user]
  saveUser --"create user"--> userDb
  saveUser --> createAuthentication[create authentication]
  createAuthentication --http callout--> authentication-rest-service
  authentication-rest-service --> createAccount[create account]
  createAccount --http callout--> account-rest-service
  
```
Check here for create [authentication](https://github.com/sonamsamdupkhangsar/authentication-rest-service/tree/feature/openapi-jan-2-2023#create-authentication-workflow) documentation.

Check here for create [account](https://github.com/sonamsamdupkhangsar/account-rest-service/tree/feature/route-update-jan-18-2023#create-account) documentation.

## Update User workflow
```mermaid
flowchart TD
 User[user-request] --> user-rest-service[update update]
 user-rest-service --> findUser[get user by authenticationId]
 findUser --> userExistByEmailAndIdNot[findUser where email is not already used by someone else]
 userExistByEmailAndIdNot --> updateFirstNameAndLastNameAndEmail["update user name and email"]
 updateFirstNameAndLastNameAndEmail --> userDb[(userdb postgresql)]
```

## Update profilephoto
```mermaid
flowchart TD
  User[user-request] --> updateProfilePhoto[update profile photo]
  updateProfilePhoto --> userDb[(userdb postgresql)]
```

## Get user by authenticationId
```mermaid
flowchart TD
  User[user-request] --> getUserByAuthenticationId
  getUserByAuthenticationId --> userDb[(userdb postgresql)]
```

## find matching name
```mermaid
flowchart TD
  User[user-request] --> findUsers[find users that have matching firstname and lastname ignorecase]
  findUsers --> userDb[(userdb postgresql)]
```

## activate user
```mermaid
flowchart TD
  User[user-request] --> activateUser[set active flag to true]
  activateUser --> userDb[(userdb postgresql)]
```

## delete user
```mermaid
flowchart TD
  User[user-request] --> deleteUser[delete user by authenticationId]
  deleteUser --> isUserActive{is user active?}
  isUserActive -->|Yes, user is already active| returnError[Return 400 to request]
  isUserActive -->|No| canDeleteUser[user can be deleted]
  canDeleteUser --> userDb[(userdb postgresql)]
```




