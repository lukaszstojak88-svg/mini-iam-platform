import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/** Short confirmations and error notices (MatSnackBar). */
@Injectable({ providedIn: 'root' })
export class NotifyService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.snackBar.open(message, 'OK', { duration: 4000 });
  }

  error(message: string): void {
    this.snackBar.open(message, 'Close', { duration: 8000, panelClass: 'snack-error' });
  }
}
