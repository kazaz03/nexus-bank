export interface Customer {
  id: number;
  userId: number;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  address: string | null;
  dateOfBirth: string | null;
  idCardNumber: string | null;
  kycStatus: string;
  createdAt: string | null;
}
