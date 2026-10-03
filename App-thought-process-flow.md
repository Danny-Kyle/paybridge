Alright, so my main aim right now is to use Virtual threads to 


We want to get the Customer details first, this should be done in the /customer folder and everything relating to the Customer is in there as well

Before we start implementation of any transfers at all we should get the customer details, but when the customer wants to create any transfers, the party they wih to transfer to needs to have the following provided:
Get Details/Credentials
    Get their acount, (Task 1)
    get their current account balance, (Task 2) 
    get their next of kin (Task 3 which creates 2 split tasks)
        Get their next of kin account (Task 4)
        get their next of kin account balance (Task 5)
Get all these details and send to the transfer service, which then routes the transfer to the appropriate party based on these set rules

Currently implementing a feature called next-of-kin so if the customer has a certain amount of money in their account at a certain time the transfer doesn't go through but gets routed to this next of kin instead