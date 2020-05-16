/**
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

Cypress.Commands.add('login', (username, password) => {
    Cypress.log({
        name: 'login',
        message: `${username} | ${password}`,
    })

    cy.visit('login')

    cy.get('#login-username').clear().type(username)
    cy.get('#login-password').clear().type(password)
    cy.get('#login-form').submit()

})

Cypress.Commands.add('createAccount', (user) => {
    Cypress.log({
        name: 'createAccount',
        message: user,
    })

    cy.visit('/signup')
    cy.get('#signup-username').type(user.username)
    cy.get('#signup-password').type(user.password)
    cy.get('#signup-password-repeat').type(user.password)
    cy.get('#signup-firstname').type(user.firstName)
    cy.get('#signup-lastname').type(user.lastName)
    cy.get('#signup-birthday').type('1981-01-01')
    cy.get('#signup-form').submit()
})

Cypress.Commands.add('deposit', (externalAccount, depositAmount) => {
    Cypress.log({
        name: 'deposit',
        message: `${externalAccount}` | `${depositAmount}`
    })

    const accountNum = externalAccount.accountNum
    const routingNum = externalAccount.routingNum

    // const template = '{"account_num": "${account_num}", "routing_num": "${routing_num}"}';
    // const makeVal = new Function("{account_num, routing_num}", "return `" + template + "`;")
    // const val = makeVal({account_num: accountNum, routing_num: routingNum})
// var makeUrl = new Function("{name, age}", "return `" + template + "`;");
// var url = makeUrl({name: "John", age: 30});
// console.log(url); //http://example.com/?name=John&age=30

    cy.get('.h5.mb-0').first().click() 
    cy.get('#depositFunds').should('be.visible') 
    // cy.get('#accounts').select(val)
    cy.get('#accounts').select('{"account_num": "9099791699", "routing_num": "808889588" }')
    // cy.get('#accounts').select('{"account_num": "9099791699", "routing_num": "808889588" }')
    // cy.get('#accounts').select(`{"account_num": "${accountNum}", "routing_num": "${routingNum}"}`)

    // cy.get('#accounts').find('option').as('selectOption').then( () => {
    //         cy.get('#accounts').select(`${this.selectOption.text()}`)
    // })

    cy.get('#deposit-amount').type(`${depositAmount}`)
    cy.get('#deposit-form').submit()
})