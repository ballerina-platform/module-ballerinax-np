import ballerina/io;
import ballerinax/np;

# Represents a person who plays a sport.
type SportsPerson record {|
    # First name of the person
    string firstName;
    # Last name of the person
    string lastName;
    # Year the person was born
    int yearOfBirth;
    # Sport that the person plays
    string sport;
|};

function getPopularSportsPerson(
        string nameSegment, 
        int decadeStart, 
        np:Prompt prompt = `Who is a popular sportsperson that was born in the decade starting 
            from ${decadeStart} with ${nameSegment} in their name?`) 
    returns SportsPerson|error? = @np:NaturalFunction external;

public function main() returns error? {
    string nameSegment = "Simone";
    int decadeStart = 1990;

    SportsPerson? person = check getPopularSportsPerson(nameSegment, decadeStart);
    if person is SportsPerson {
        io:println("Full name: ", person.firstName, " ", person.lastName);
        io:println("Born in: ", person.yearOfBirth);
        io:println("Sport: ", person.sport);
    } else {
        io:println("No matching sportsperson found");
    }
}
