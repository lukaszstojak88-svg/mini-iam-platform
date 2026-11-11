import { Component, inject, Injectable } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { map, Observable } from 'rxjs';

export interface ConfirmData {
  title: string;
  message: string;
  confirmLabel: string;
  /** Destructive actions get a red confirm button. */
  danger?: boolean;
}

/** Confirmation dialog for irreversible or significant actions. */
@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false">Cancel</button>
      <button mat-flat-button type="button" [class.danger]="data.danger" [mat-dialog-close]="true" cdkFocusInitial>
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .danger {
      --mat-button-filled-container-color: var(--app-danger);
      --mat-button-filled-label-text-color: #ffffff;
    }
  `,
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmData>(MAT_DIALOG_DATA);
}

@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly dialog = inject(MatDialog);

  /** Emits `true` only when the user confirmed. */
  confirm(data: ConfirmData): Observable<boolean> {
    return this.dialog
      .open<ConfirmDialog, ConfirmData, boolean>(ConfirmDialog, { data, width: '440px', autoFocus: 'dialog' })
      .afterClosed()
      .pipe(map((result) => result === true));
  }
}
