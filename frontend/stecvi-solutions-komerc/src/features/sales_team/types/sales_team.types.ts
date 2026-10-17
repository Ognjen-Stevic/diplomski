export type TeamMemberSummary = {
    salesRepId: number;
    firstName: string;
    lastName: string;
    email: string;
    phone: string | null;
    activeCustomers: number;
    currentMonthRequisitions: number;
    active: boolean;
};

export type ManagerTeamSummary = {
    managerId: number;
    firstName: string;
    lastName: string;
    email: string;
    phone: string | null;
    active: boolean;
    teamMemberCount: number;
    activeCustomers: number;
    currentMonthRequisitions: number;
};

export type TeamSummaryMember = TeamMemberSummary | ManagerTeamSummary;

export type TeamComparisonMember = {
    salesRepId: number;
    firstName: string;
    lastName: string;
    bottlesSold: number;
    balloonsSold: number;
    gratisBottles: number;
    gratisBalloons: number;
    stimulation: number;
};

export type TeamComparison = {
    year: number;
    month: number;
    salesRepresentatives: TeamComparisonMember[];
    managerStimulation: number;
};

export type ManagerComparisonMember = {
    managerId: number;
    firstName: string;
    lastName: string;
    bottlesSold: number;
    balloonsSold: number;
    gratisBottles: number;
    gratisBalloons: number;
    stimulation: number;
};

export type ManagersComparison = {
    year: number;
    month: number;
    managers: ManagerComparisonMember[];
};
