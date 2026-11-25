import React from 'react';
import { render, screen, fireEvent, within } from '@testing-library/react';
import MultiSelectDropdown from '../../components/MultiSelectDropdown';

const OPTIONS = [
  { value: '1', label: 'Alpha' },
  { value: '2', label: 'Beta' },
  { value: '3', label: 'Gamma' },
];

const renderComponent = (overrides = {}) => {
  const onChange = jest.fn();
  const props = {
    id: 'thing-filter',
    label: 'Thing',
    options: OPTIONS,
    selected: [],
    onChange,
    allLabel: 'All Things',
    ...overrides,
  };
  render(<MultiSelectDropdown {...props} />);
  return { onChange };
};

describe('MultiSelectDropdown component', () => {
  test('shows the allLabel summary when nothing is selected', () => {
    renderComponent();
    expect(screen.getByRole('button', { name: 'All Things' })).toBeInTheDocument();
  });

  test('shows the single option label when exactly one is selected', () => {
    renderComponent({ selected: ['2'] });
    expect(screen.getByRole('button', { name: 'Beta' })).toBeInTheDocument();
  });

  test('shows "N selected" when more than one is selected', () => {
    renderComponent({ selected: ['1', '2'] });
    expect(screen.getByRole('button', { name: '2 selected' })).toBeInTheDocument();
  });

  test('clicking the toggle opens a checkbox panel listing every option', () => {
    renderComponent();
    fireEvent.click(screen.getByRole('button', { name: 'All Things' }));
    const panel = screen.getByRole('listbox', { name: 'Thing' });
    expect(within(panel).getByRole('checkbox', { name: 'Alpha' })).toBeInTheDocument();
    expect(within(panel).getByRole('checkbox', { name: 'Beta' })).toBeInTheDocument();
    expect(within(panel).getByRole('checkbox', { name: 'Gamma' })).toBeInTheDocument();
  });

  test('checking an option calls onChange with it added to the selection', () => {
    const { onChange } = renderComponent({ selected: ['1'] });
    fireEvent.click(screen.getByRole('button', { name: 'Alpha' }));
    const panel = screen.getByRole('listbox', { name: 'Thing' });
    fireEvent.click(within(panel).getByRole('checkbox', { name: 'Beta' }));
    expect(onChange).toHaveBeenCalledWith(['1', '2']);
  });

  test('unchecking a selected option calls onChange with it removed', () => {
    const { onChange } = renderComponent({ selected: ['1', '2'] });
    fireEvent.click(screen.getByRole('button', { name: '2 selected' }));
    const panel = screen.getByRole('listbox', { name: 'Thing' });
    fireEvent.click(within(panel).getByRole('checkbox', { name: 'Alpha' }));
    expect(onChange).toHaveBeenCalledWith(['2']);
  });

  test('checking the "All" checkbox clears the selection', () => {
    const { onChange } = renderComponent({ selected: ['1', '2'] });
    fireEvent.click(screen.getByRole('button', { name: '2 selected' }));
    const panel = screen.getByRole('listbox', { name: 'Thing' });
    fireEvent.click(within(panel).getByRole('checkbox', { name: 'All Things' }));
    expect(onChange).toHaveBeenCalledWith([]);
  });

  test('the "All" checkbox is checked only when nothing is selected', () => {
    renderComponent({ selected: [] });
    fireEvent.click(screen.getByRole('button', { name: 'All Things' }));
    const panel = screen.getByRole('listbox', { name: 'Thing' });
    expect(within(panel).getByRole('checkbox', { name: 'All Things' })).toBeChecked();
  });

  test('clicking outside the panel closes it', () => {
    renderComponent();
    fireEvent.click(screen.getByRole('button', { name: 'All Things' }));
    expect(screen.getByRole('listbox', { name: 'Thing' })).toBeInTheDocument();
    fireEvent.mouseDown(document.body);
    expect(screen.queryByRole('listbox', { name: 'Thing' })).not.toBeInTheDocument();
  });

  test('pressing Escape closes the panel', () => {
    renderComponent();
    fireEvent.click(screen.getByRole('button', { name: 'All Things' }));
    expect(screen.getByRole('listbox', { name: 'Thing' })).toBeInTheDocument();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('listbox', { name: 'Thing' })).not.toBeInTheDocument();
  });
});
